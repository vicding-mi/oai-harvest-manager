oai-harvest-manager
===================


OAI Harvest Manager is a simple Java application for managing OAI-PMH
harvesting. It is intended to allow definition of a harvesting
workflow (involving OAI harvesting and subsequent operations like
transformations or mappings of metadata between schemata) in a few
minutes using a configuration file only.

This application does not itself contain an implementation of the
OAI-PMH protocol; it uses the [OCLC harvester2
library](https://code.google.com/p/oaiharvester2/) for OAI-PMH
requests.


# Basic Glossary

In OAI-PMH, an individual metadata datum is called a
**record**. Clients, such as this application, that fetch records are
called **harvesters**. The server application from which records are
obtained is called a **provider**. The base URL of the provider
(i.e. the request URL without any parameters) is also called an
OAI-PMH **endpoint**.


# Building

Building this app requires JDK 1.7 and Apache Maven. It can be built
simply using the command:

```mvn clean package assembly:assembly```

If you use a Java IDE, it is highly likely it also offers a simple way
to do the above.

The above build process creates a package named
`oai-harvest-manager-x.y.z.tar.gz` (where x.y.z is a version number).


# Running the Application

There are no installation instructions to speak of: simply unpack the
above package into wherever you like. The deployment package contains
aa script to start the app, `run-harvester.sh` (for Unix systems
including Mac OS X; we can add a Windows batch file if anyone wants
it). The simplest usage is:

```run-harvester.sh config.xml```

where `config.xml` is the configuration file you wish to use.
Additionally, parameters can be defined on the command line. For
example:

```run-harvester.sh timeout=30 config.xml```

will set the connection timeout to 30 seconds. This value will
override the timeout value defined in `config.xml`, if any. The first
parameter that does not contain = is taken as the configuration file
name.


# Configuration

The behaviour of the app is determined by a single configuration
file. The configuration file is composed of four sections:

- *settings*, where options such as directory paths and timeouts are
   set;
- *directories*, where output paths are defined;
- *actions*, the most complex section, where sequences of actions can
   be defined for different metadata formats (actions include semantic
   transformations and saving intermediary or final results into a
   file); and
- *providers*, where endpoints for the providers to be harvested are
   listed.

To get a clear idea of the structure of the configuration file, see
the [sample configuration files](src/main/resources) in juxtaposition
with the explanation for each section below.

## Configuring Settings

The configuration parameters in this section govern the working
directory (all output directories will be interpreted relative to it);
connection limits including retry count, connection delay and timeout;
thread control settings, including the resource pool size (which can
be reduced to lessen memory footprint, or increased to speed up
processing if resources are plentiful); and settings related to
incremental harvesting.

## Configuring Directories

The output paths listed in this section must each be given a unique
identifier. Additionally, the `max-files` attribute can be used to set
a limit on the number of files in a single directory. If this is
non-zero, subdirectories will be created in such a way that each
subdirectory has at most `max-files` files in it. The usefulness of
this setting largely depends on the total number of records you expect
to store in a single directory and the file system used.

## Configuring Actions

Multiple action sequences can be defined in this section. Each
sequence corresponds to a format specification followed by a number of
sequential actions.

The **format definition** is made up of a match type (attribute
*match*) and match value (attribute *value*). The match type is one of
```prefix```, which simply specifies and OAI-PMH metadata prefix,
```namespace```, and ```schema```. When one of the latter two types is
used, the harvest manager will contact the provider with a
```ListMetadataFormats``` query and choose *all* metadata prefixes
that correspond to the specified namespace or schema.

The **actions** are manipulations of the metadata record, each of
which operates on the result of the previous action. A number of
action types are available:

- The *save* action stores the record in a new file in a specified
  output directory, specified by an identifier matching one of the
  directories defined in the previous section. The attribute *suffix*
  can be used to specify the file extension (the most typical value
  being ```suffix=".xml"`). If the attribute *group-by-provider* is
  specified, a separate subdirectory will be created for each
  endpoint.

- The *strip* action removes the OAI-PMH envelope and retains only the
  actual metadata record. Note that the envelope contains information
  not found within the record itself, such as the date it was fetched
  and its OAI-PMH identifier.

- The *transform* action applies a mapping, defined in an XSLT file,
  to the metadata record. This can be used, among other things, for
  semantic mapping between metadata schemata. See the included
  configuration files for an example.

For each provider, the first format definition that the provider
supports will determine the action sequence to be executed. If one of
the actions in a sequence fails, the subsequent actions are not
carried out and an error message is logged (but processing of any
other metadata record is unaffected).

## Configuring Providers

For each provider, the following can be defined:

- The *url* attribute (mandatory) specifies the endpoint. Any URL
  parameters (for example, `?verb=Identify` is commonly included
  when endpoint addresses are discussed) are unnecessary and will be
  stripped off automatically.

- The *name* attribute specifies the name to use for the provider
  (which may in turn determine file paths, depending on other
  settings). If no name is specified, the provider will be contacted
  and the name from its `Identify` response used. If no valid response
  is received within a reasonable time, a generic string like
  **Unnamed provider at oai.xyz.org** is used instead.

- The provider element may contain multiple *set* child elements,
  which specify the names of OAI-PMH sets to be harvested.

There is also a special case where provider names may be imported from
a *centre registry*, so far only used by the CLARIN community.


# Implementation Notes

Saxon is used as the XPath engine, although only standard APIs are
used and hence changing to a different XPath processor would be
trivial.

Processing for each provider runs in a separate thread. It is not
possible to target a single provider with multiple threads (except in
the special case where sets are used; then it is possible to mention
the provider multiple times in the provider list, each with different
set(s), and the multiple references to the same provider will then be
treated like different providers).

For efficiency, thread pools containing prepared action objects are
constructed for each action referenced in the actions section of the
configuration file. Different action sequences share the same pool for
the exact same action. Consider the following example, assuming that
the configuration parameter *resource-pool-size* is set to 5:

```xml
    <format match="namespace" value="http://www.clarin.eu/cmd/">
      <action type="save" dir="orig"/>
      <action type="strip"/>
      <action type="save" dir="cmdi"/>
    </format>
    <format match="prefix" value="olac">
      <action type="save" dir="orig"/>
      <action type="strip"/>
      <action type="save" dir="olac" group-by-provider="false"/>
    </format>
```

In this case, a total of 15 objects are pooled for the save actions: 5
for saving to the directory ```orig``` in a pool shared by the two
action sequences, and 5 each for the directories ```cmdi``` and
```olac```, only used by one action sequence each.

The pooling implementation is particularly important when
transformations are used, as preparing a transformation object
involves parsing the XSLT, potentially a time-consuming process.


# Build Status

[![Build Status](https://travis-ci.org/TheLanguageArchive/oai-harvest-manager.png?branch=master)](https://travis-ci.org/TheLanguageArchive/oai-harvest-manager)
