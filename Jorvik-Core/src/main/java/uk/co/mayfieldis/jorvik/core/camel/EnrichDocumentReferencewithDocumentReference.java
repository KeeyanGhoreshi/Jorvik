package uk.co.mayfieldis.jorvik.core.camel;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.DocumentReference;

import ca.uhn.fhir.context.FhirContext;

import ca.uhn.fhir.parser.IParser;


public class EnrichDocumentReferencewithDocumentReference implements AggregationStrategy {

//	private static final Logger log = LoggerFactory.getLogger(uk.co.mayfieldis.jorvik.core.EnrichPatientwithPatient.class);
	
	public EnrichDocumentReferencewithDocumentReference(FhirContext ctx)
	{
		this.ctx = ctx;
	}
	
	private FhirContext ctx;
	
	@Override
	public Exchange aggregate(Exchange exchange, Exchange enrichment) {
		
		Bundle bundle = null;
		
		DocumentReference documentReference = null;
		
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
						bundle = parser.parseResource(Bundle.class, reader);
					}
					catch(Exception ex)
					{
						ex.printStackTrace();
						throw ex;
					}
				}
				else
				{
					// XmlParser parser = new XmlParser();
					IParser parser = ctx.newXmlParser();
					try
					{
						bundle = parser.parseResource(Bundle.class, reader);
					}
					catch(Exception ex)
					{
						ex.printStackTrace();
						throw ex;
					}
				}
				//ByteArrayInputStream xmlNewContentBytes = new ByteArrayInputStream ((byte[]) exchange.getIn().getBody(byte[].class));
				Reader readerNew = new InputStreamReader(new ByteArrayInputStream ((byte[]) exchange.getIn().getBody(byte[].class)));
				//XmlParser parser = new XmlParser();
				IParser parser = ctx.newXmlParser();
				try
				{
					documentReference =  parser.parseResource(DocumentReference.class,readerNew);
					if (bundle.getEntry().size()>0)
					{
						DocumentReference hapiDocumentReference = (DocumentReference) bundle.getEntry().get(0).getResource();  
						documentReference.setId(hapiDocumentReference.getId());
						exchange.getIn().setHeader(Exchange.HTTP_METHOD,"PUT");
						exchange.getIn().setHeader(Exchange.HTTP_PATH,"DocumentReference/"+hapiDocumentReference.getId());
						exchange.getIn().setHeader(Exchange.HTTP_URI,"");
						// Have altered resource so process it.
						String Response = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(documentReference);
						//String Response = ResourceSerialiser.serialise(documentReference, ParserType.XML);
						exchange.getIn().setBody(Response);
					}
					else
					{
						exchange.getIn().setHeader(Exchange.HTTP_METHOD,"POST");
						exchange.getIn().setHeader(Exchange.HTTP_PATH,"DocumentReference");
						exchange.getIn().setHeader(Exchange.HTTP_URI,"");
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
			ex.printStackTrace();
			throw ex;
		}
		
		return exchange;
	}

}

