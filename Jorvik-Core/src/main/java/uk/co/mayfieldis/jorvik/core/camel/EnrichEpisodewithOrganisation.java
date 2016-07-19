package uk.co.mayfieldis.jorvik.core.camel;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.EpisodeOfCare;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Reference;


import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;


public class EnrichEpisodewithOrganisation implements AggregationStrategy {

//	private static final Logger log = LoggerFactory.getLogger(uk.co.mayfieldis.jorvik.core.EnrichEncounterwithOrganisation.class);
	public EnrichEpisodewithOrganisation(FhirContext ctx)
	{
		this.ctx = ctx;
		
	}
	private  FhirContext ctx;
	@Override
	public Exchange aggregate(Exchange exchange, Exchange enrichment) {
		
		Bundle bundle = null;
		
		EpisodeOfCare episode = null;
		
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
					if (bundle.getEntry().size()>0)
					{
						episode = parser.parseResource(EpisodeOfCare.class,readerNew);
						Reference ref = new Reference();
						Organization organisation = (Organization) bundle.getEntry().get(0).getResource(); 
						ref.setReference("Organization/"+organisation.getId());
						exchange.getIn().setHeader("FHIROrganisationRef","Organization/"+organisation.getId());
						episode.setManagingOrganization(ref);
						String Response = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(episode);
						//String Response = ResourceSerialiser.serialise(episode, ParserType.XML);
						exchange.getIn().setBody(Response);
					}
				}
				catch(Exception ex)
				{
					
					ex.printStackTrace();
					throw ex;
				}
				
			
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
