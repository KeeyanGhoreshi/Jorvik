package uk.co.mayfieldis.jorvik.core.camel;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.hl7.fhir.dstu3.model.Practitioner.PractitionerPractitionerRoleComponent;
import org.hl7.fhir.dstu3.model.Reference;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.Bundle;
import ca.uhn.fhir.parser.IParser;

import uk.co.mayfieldis.jorvik.core.FHIRConstants.FHIRCodeSystems;

public class EnrichConsultantwithOrganisation implements AggregationStrategy  {

//	private static final Logger log = LoggerFactory.getLogger(uk.co.mayfieldis.jorvik.core.EnrichConsultantwithOrganisation.class);
	
	public FhirContext ctx;
	
	@Override
	public Exchange aggregate(Exchange exchange, Exchange enrichment) 
	{
		
		//FhirContext ctx = FhirContext.forDstu3();
		
		Organization parentOrganisation = null;
		Practitioner gp = null;
		//
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
					Bundle bundle = parser.parseBundle(reader);
					if (bundle.getEntries().size()>0)
					{
						parentOrganisation = (Organization) bundle.getEntries().get(0).getResource();
					}
				}
				catch(Exception ex)
				{
					
				}
			}
			else
			{
				// XmlParser parser = new XmlParser();
				IParser parser = ctx.newXmlParser();
				
				try
				{
					Bundle bundle = parser.parseBundle(reader);
					if (bundle.getEntries().size()>0)
					{
						parentOrganisation = (Organization) bundle.getEntries().get(0).getResource();
					}
				}
				catch(Exception ex)
				{
					
				}
			}
			
			if (parentOrganisation !=null)
			{
				//ByteArrayInputStream xmlNewContentBytes = new ByteArrayInputStream ((byte[]) exchange.getIn().getBody(byte[].class));
				Reader readerNew = new InputStreamReader(new ByteArrayInputStream ((byte[]) exchange.getIn().getBody(byte[].class)));
				
				IParser parser = ctx.newXmlParser();
				try
				{
					// Add in the parent organisation code
					gp = parser.parseResource(Practitioner.class, readerNew);
					
					PractitionerPractitionerRoleComponent practitionerRole = gp.getPractitionerRole().get(0);
									
					Reference organisation = new Reference();
					organisation.setReference("Organization/"+parentOrganisation.getId());
					practitionerRole.setOrganization(organisation);
					Extension parentOrg= new Extension();
					parentOrg.setUrl(FHIRCodeSystems.URI_NHS_OCS_ORGANISATION_CODE+"/ParentCode");
					CodeableConcept parentCode = new CodeableConcept();
					parentCode.addCoding()
						.setCode(exchange.getIn().getHeader("ParentOrganisationCode").toString())
						.setSystem(FHIRCodeSystems.URI_NHS_OCS_ORGANISATION_CODE);
					
					parentOrg.setValue(parentCode);
					practitionerRole.addExtension(parentOrg);
					String Response = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(gp);
					//String Response = ResourceSerialiser.serialise(gp, ParserType.XML);
					exchange.getIn().setBody(Response);
				}
				catch(Exception ex)
				{
					ex.printStackTrace();
					//throw ex;
				}
			}
		}
		
		exchange.getIn().setHeader(Exchange.CONTENT_TYPE,"application/xml+fhir");
		exchange.getIn().setHeader(Exchange.HTTP_METHOD,"GET");
		return exchange;
	}
}


