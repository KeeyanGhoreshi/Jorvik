package uk.co.mayfieldis.jorvik.core.camel;

import java.io.ByteArrayInputStream;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.hl7.fhir.instance.formats.JsonParser;
import org.hl7.fhir.instance.formats.ParserType;
import org.hl7.fhir.instance.formats.XmlParser;
import org.hl7.fhir.instance.model.Bundle;
import org.hl7.fhir.instance.model.EpisodeOfCare;
import org.hl7.fhir.instance.model.EpisodeOfCare.EpisodeOfCareCareTeamComponent;
import org.hl7.fhir.instance.model.Practitioner;
import org.hl7.fhir.instance.model.Reference;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

public class EnrichEpisodewithPractitioner implements AggregationStrategy {

//	private static final Logger log = LoggerFactory.getLogger(uk.co.mayfieldis.jorvik.core.EnrichEncounterwithPractitioner.class);
	
	@Override
	public Exchange aggregate(Exchange exchange, Exchange enrichment) {
		
		Bundle bundle = null;
		
		EpisodeOfCare episode = null;
		
		try
		{
			exchange.getIn().setHeader(Exchange.HTTP_METHOD,"GET");

			if (enrichment.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE).toString().equals("200") && enrichment.getIn().getBody() != null)
			{
				
				ByteArrayInputStream xmlContentBytes = new ByteArrayInputStream ((byte[]) enrichment.getIn().getBody(byte[].class));
				
				
					if (enrichment.getIn().getHeader(Exchange.CONTENT_TYPE).toString().contains("json"))
					{
						JsonParser composer = new JsonParser();
						try
						{
							bundle = (Bundle) composer.parse(xmlContentBytes);
						}
						catch(Exception ex)
						{
	//						log.error("#9 JSON Parse failed "+ex.getMessage());
						}
					}
					else
					{
						XmlParser composer = new XmlParser();
						try
						{
							bundle = (Bundle) composer.parse(xmlContentBytes);
						}
						catch(Exception ex)
						{
	//						log.error("#10 XML Parse failed "+ex.getMessage());
						}
					}
					ByteArrayInputStream xmlNewContentBytes = new ByteArrayInputStream ((byte[]) exchange.getIn().getBody(byte[].class));
					
					XmlParser composer = new XmlParser();
					try
					{
						if (bundle.getEntry().size()>0)
						{
							episode = (EpisodeOfCare) composer.parse(xmlNewContentBytes);
							Reference ref = new Reference();
							Practitioner practitioner = (Practitioner) bundle.getEntry().get(0).getResource(); 
							ref.setReference("Practitioner/"+practitioner.getId());
							EpisodeOfCareCareTeamComponent team = null;
							if (episode.getCareTeam().size() == 0)
							{
								team = episode.addCareTeam();
							}
							else
							{
								team = episode.getCareTeam().get(0);
							}
							team.setMember(ref);
							
							String Response = ResourceSerialiser.serialise(episode, ParserType.XML);
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
		//	log.error(exchange.getExchangeId() + " "  + ex.getMessage() +" " + enrichment.getProperties().toString());
		}
		
		return exchange;
	}

}
