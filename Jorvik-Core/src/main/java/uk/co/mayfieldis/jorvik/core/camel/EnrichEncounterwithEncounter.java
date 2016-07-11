package uk.co.mayfieldis.jorvik.core.camel;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.hl7.fhir.dstu3.model.Encounter;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.Bundle;
import ca.uhn.fhir.parser.IParser;



public class EnrichEncounterwithEncounter implements AggregationStrategy {

//	private static final Logger log = LoggerFactory.getLogger(uk.co.mayfieldis.jorvik.core.EnrichEncounterwithEncounter.class);
	
	public FhirContext ctx;
	
	@Override
	public Exchange aggregate(Exchange exchange, Exchange enrichment) {
		
		Bundle bundle = null;
		
		Encounter encounter = null;
		
		try
		{
			exchange.getIn().setHeader(Exchange.HTTP_METHOD,"GET");

			if (enrichment.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE).toString().equals("200"))
			{
				
				//ByteArrayInputStream xmlContentBytes = new ByteArrayInputStream ((byte[]) enrichment.getIn().getBody(byte[].class));
				Reader reader = new InputStreamReader(new ByteArrayInputStream ((byte[]) enrichment.getIn().getBody(byte[].class)));
				
				if (enrichment.getIn().getHeader(Exchange.CONTENT_TYPE).toString().contains("json"))
				{
					//JsonParser parser = new JsonParser();
					IParser parser = ctx.newJsonParser();
					
					try
					{
						bundle = parser.parseBundle(reader);
					}
					catch(Exception ex)
					{
	//					log.error("#9 JSON Parse failed "+ex.getMessage());
					}
				}
				else
				{
					// XmlParser parser = new XmlParser();
					IParser parser = ctx.newXmlParser();
					try
					{
						bundle = parser.parseBundle(reader);
					}
					catch(Exception ex)
					{
		//				log.error("#10 XML Parse failed "+ex.getMessage());
					}
				}
				//ByteArrayInputStream xmlNewContentBytes = new ByteArrayInputStream ((byte[]) exchange.getIn().getBody(byte[].class));
				Reader readerNew = new InputStreamReader(new ByteArrayInputStream ((byte[]) exchange.getIn().getBody(byte[].class)));
				//XmlParser parser = new XmlParser();
				IParser parser = ctx.newXmlParser();
				try
				{
					//encounter = (Encounter) parser.parseResource(xmlNewContentBytes);
					encounter = (Encounter) parser.parseResource(readerNew);
					if (bundle.getEntries().size()>0)
					{
						Encounter hapiEncounter = (Encounter) bundle.getEntries().get(0).getResource();  
						encounter.setId(hapiEncounter.getId());
						exchange.getIn().setHeader(Exchange.HTTP_METHOD,"PUT");
						exchange.getIn().setHeader(Exchange.HTTP_PATH,"Encounter/"+hapiEncounter.getId());
						// Have altered resource so process it.
						//String Response = ResourceSerialiser.serialise(encounter, ParserType.XML);
						String Response = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(encounter);
						exchange.getIn().setBody(Response);
					}
					else
					{
						exchange.getIn().setHeader(Exchange.HTTP_METHOD,"POST");
						exchange.getIn().setHeader(Exchange.HTTP_PATH,"Encounter");
						
					}
				}
				catch(Exception ex)
				{
					ex.printStackTrace();
					throw ex;
				}
				
				exchange.getIn().setHeader(Exchange.HTTP_QUERY,"");
				exchange.getIn().setHeader(Exchange.CONTENT_TYPE,"application/xml+fhir");
			}
		}
		catch (Exception ex)
		{
		//	log.error(exchange.getExchangeId() + " "  + ex.getMessage() +" " + enrichment.getProperties().toString());
		}
		
		return exchange;
	}

}

