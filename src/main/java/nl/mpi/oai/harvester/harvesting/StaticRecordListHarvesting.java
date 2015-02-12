package nl.mpi.oai.harvester.harvesting;

import nl.mpi.oai.harvester.StaticProvider;
import nl.mpi.oai.harvester.metadata.Metadata;
import nl.mpi.oai.harvester.Provider;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import java.util.List;

/**
 * <br> Get metadata records <br><br>
 *
 * Applying static harvesting means that before getting the prefixes, the
 * static contents needs to be fetched from the provider. In fact, in static
 * harvesting, this is all that will be available. Because the records are
 * in this document also, it needs to be passed to other applications of the
 * protocol.<br><br> kj: remove redundancy
 *
 * Note: this class defines what is different from fetching prefixes from a non
 * static endpoint.
 *
 * kj: there are no sets in static harvesting
 *
 * @author Kees Jan van de Looij (MPI-PL)
 */
public class StaticRecordListHarvesting extends AbstractListHarvesting {

    private static final Logger logger = Logger.getLogger(
            AbstractListHarvesting.class);

    /**
     * <br> Associate provider data and desired prefixes
     *
     * @param provider the provider
     * @param prefixes the prefixes obtained from the static content
     */
    public StaticRecordListHarvesting(Provider provider, List<String> prefixes) {
        super(provider);
        this.prefixes = prefixes;
    }

    /**
     * <br> Verify if the static content is in place
     *
     * @return false if there was an error, true otherwise
     */
    @Override
    public boolean request() {

        // check for protocol errors

        if (pIndex >= prefixes.size()) {
            logger.error("Protocol error");
            return false;
        }

        if (provider.sets != null){
            if (sIndex >= provider.sets.length){
                logger.error("Protocol error");
                return false;
            }
        }

        if (! (provider instanceof StaticProvider)){
            logger.error("Protocol error"); return false;
        } else {
            StaticProvider p = (StaticProvider) provider;
            response = p.getResponse();

            if (response == null){
                // a response should be there
                return false;
            } else {
                // the original response is there
                return true;
            }
        }
    }

    /**
     * <br> Get the response
     *
     * @return the response
     */
    @Override
    public Document getResponse() {

        if (! (provider instanceof StaticProvider)){
            logger.error("Protocol error"); return null;
        } else {
            StaticProvider p = (StaticProvider) provider;
            response = p.getResponse();

            if (response == null){
                // content should be there
                return null;
            } else {
                // static content is in place

                return response.getDocument();
            }
        }
    }

    /**
     * <br> Check if there is more
     *
     * @return false
     */
    @Override
    public boolean requestMore() {
        /* Since he response contains everything that can be harvested for this
           provider and prefix, there is no need to ask for more
         */
        return false;
    }

    /**
     * <br> Get a list of records from the response
     *
     * @return  false if there was an error, true otherwise
     */
    @Override
    public boolean processResponse() {

        // check for protocol errors

        if (response == null){
            // response should be there
            logger.error("Protocol error"); return false;
        }

        if (prefixes.size() == 0){
            logger.error ("Protocol error");
        }

        // expression for parsing the static content
        String expression = "/os:Repository/os:ListRecords[@metadataPrefix = '"+
                prefixes.get(pIndex) +"']";

        // get the records associated with the prefix

        try {
            nodeList = (NodeList) provider.xpath.evaluate(expression,
                    response.getDocument(),
                    XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            /* Something went wrong when creating the list of records,
               try another prefix.
             */
            logger.error(e.getMessage(), e);
            logger.info("Cannot create list of " + prefixes.get(pIndex) +
                    " records for endpoint " + provider.oaiUrl);
            return false;
        }

        // nodeList contains records, turn the list into a document

        Node node = nodeList.item(0);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = null;
        try {
            db = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        Document document = db.newDocument();
        document.appendChild(document.importNode(node, true));

        // obtain the identifiers from the document

        expression = "//*[starts-with(local-name(),'identifier') " +
                "and parent::*[local-name()='header' " +
                "and not(@status='deleted')]]/text()";
        try{
            nodeList = (NodeList)provider.xpath.evaluate(expression,
                    document, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            /* Something went wrong when creating the list of identifiers of
               records, try another prefix.
             */
            logger.error(e.getMessage(), e);
            logger.info ("Cannot create list of identifiers of " +
                    prefixes.get(pIndex) +
                    " records for endpoint " + provider.oaiUrl);
            return false;
        }

        // add the identifier and prefix targets into the array

        for (int j = 0; j < nodeList.getLength(); j++) {
            String identifier = nodeList.item(j).getNodeValue();
            IdPrefix pair = new IdPrefix(identifier,
                    prefixes.get(pIndex));
            targets.checkAndInsertSorted(pair);
        }

        // the prefix identifier pair list is ready
        return true;
    }

    /**
     * <br> kj: doc, check if the cast to document works
     *
     * @return
     */
    @Override
    public Object parseResponse() {

        // check for protocol error
        if (tIndex >= targets.size()) {
            logger.error("Protocol error");
            return null;
        }

        IdPrefix pair = targets.get(tIndex);
        tIndex++;

        // expression for parsing the document
        String expression = "/os:Repository/os:ListRecords[@metadataPrefix = '"
                + pair.prefix +
                "']/oai:record[./oai:header/oai:identifier/text() = '"
                + pair.identifier + "']";

        // get the record for the identifier and prefix
        Node dataNode;
        Document document = response.getDocument();

        // kj:

        try {
            dataNode = (Node) provider.xpath.evaluate(expression,
                    document, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            // something went wrong when parsing, try another prefix
            logger.error(e.getMessage(), e);
            logger.info("Cannot create list of identifiers of " + pair.prefix +
                    " records for endpoint " + provider.oaiUrl);
            return null;
        }

        // create a document to store the metadata in
        // kj: check of the node needs to be cloned
        // dataNode = dataNode.cloneNode(true);
        document = provider.db.newDocument();
        Node copy = document.importNode(dataNode, true);
        document.appendChild(copy);

        // everything is fine, return the record
        return new Metadata(pair.identifier, document, provider, false, false);
    }
}
