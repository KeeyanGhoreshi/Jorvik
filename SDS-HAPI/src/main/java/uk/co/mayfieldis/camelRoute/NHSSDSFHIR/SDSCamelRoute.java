package uk.co.mayfieldis.camelRoute.NHSSDSFHIR;

import java.util.Iterator;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.zipfile.ZipFileDataFormat;
import org.apache.camel.model.dataformat.BindyType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import uk.co.mayfieldis.jorvik.NHSSDS.NHSConsultantEntities;
import uk.co.mayfieldis.jorvik.NHSSDS.NHSConsultantEntitiestoFHIRPractitioner;
import uk.co.mayfieldis.jorvik.NHSSDS.NHSEntities;
import uk.co.mayfieldis.jorvik.NHSSDS.NHSEntitiestoFHIRResource;
import uk.co.mayfieldis.jorvik.NHSSDS.NHSTrustLocationEntities;
import uk.co.mayfieldis.jorvik.NHSSDS.NHSTrustLocationEntitiestoFHIRLocation;
import uk.co.mayfieldis.jorvik.core.FHIRConstants.FHIRCodeSystems;
import uk.co.mayfieldis.jorvik.core.FHIRConstants.NHSTrustFHIRCodeSystems;
import uk.co.mayfieldis.jorvik.core.camel.EnrichConsultantwithOrganisation;
import uk.co.mayfieldis.jorvik.core.camel.EnrichLocationwithLocation;
import uk.co.mayfieldis.jorvik.core.camel.EnrichLocationwithOrganisation;
import uk.co.mayfieldis.jorvik.core.camel.EnrichResourcewithOrganisation;
import uk.co.mayfieldis.jorvik.core.camel.EnrichwithUpdateType;

@Component
@PropertySource("classpath:HAPINHSSDS.properties")
public class SDSCamelRoute extends RouteBuilder {

	@Autowired
	protected Environment env;
	
	
    @Override
    public void configure() 
    {
    	// only use one context. Expensive to create - possibly look at making this config.
    	FhirContext ctx = FhirContext.forDstu3();
    	
    	NHSTrustFHIRCodeSystems TrustFHIRSystems = new NHSTrustFHIRCodeSystems();
    	TrustFHIRSystems.setValues(env);
    	
    	ZipFileDataFormat zipFile = new ZipFileDataFormat();
    	zipFile.setUsingIterator(true);
    	
    	EnrichLocationwithLocation enrichLocationwithLocation = new EnrichLocationwithLocation(ctx);
    	EnrichLocationwithOrganisation enrichLocationwithOrganisation = new EnrichLocationwithOrganisation(ctx);
    	EnrichResourcewithOrganisation enrichOrg = new EnrichResourcewithOrganisation(ctx);
    	EnrichwithUpdateType enrichUpdateType = new EnrichwithUpdateType(ctx);
    	NHSConsultantEntitiestoFHIRPractitioner consultanttoFHIRPractitioner = new NHSConsultantEntitiestoFHIRPractitioner(ctx);
    	EnrichConsultantwithOrganisation consultantEnrichwithOrganisation = new EnrichConsultantwithOrganisation(ctx);
    	
    	NHSTrustLocationEntitiestoFHIRLocation trustLocationEntitiestoFHIRLocation = new NHSTrustLocationEntitiestoFHIRLocation(ctx, TrustFHIRSystems);
    	
    	NHSEntitiestoFHIRResource nhsEntitiestoFHIRResource = new NHSEntitiestoFHIRResource(ctx);
    	
    	
    	errorHandler(deadLetterChannel("direct:error")
        		.maximumRedeliveries(2));
            	    
    	    from("direct:error")
            	.routeId("NHS SDS Fail Handler")
            	.to("log:uk.co.mayfieldis.esb.SDSHAPI.SDSCamelRoute?level=ERROR&showAll=true");
    	
    	
    		// Should follow Practice upload otherwise practice won't exist
    	    
    	    from("scheduler://egpcur?delay=24h")
    	    	.routeId("Retrieve NHS GP and Practice Amendments Zip")
    	    	.setHeader(Exchange.HTTP_METHOD, constant("GET"))
    	    	.to(env.getProperty("NHSSDS.Amendments"))
    	    	.to(env.getProperty("NHSSDS.ZipOut")+"${date:now:yyyyMMdd}-egpcur.zip");
    	  	
    	    from(env.getProperty("NHSSDS.Zip"))
	    		.routeId("Unzip NHS Reference Files")
	    		.unmarshal(zipFile)
	    		.split(body(Iterator.class))
	    			.streaming()
	    				.to("log:uk.co.mayfieldis.esb.SDSHAPI.SDSCamelRoute.zip?level=INFO")
	    				.to(env.getProperty("NHSSDS.ExtractOut"))
	    			.end()
	    		.end();
    	    
    	    from(env.getProperty("NHSSDS.Extract"))
    	    	.routeId("Split CSV File")
    	    	.log("File ${header.CamelFileName}")
    	    	.choice()
    	    		.when(header(Exchange.FILE_NAME).isEqualTo("econcur.csv"))
    	    			.to("vm:ConsultantProcessing")
    	    		.when(header(Exchange.FILE_NAME).contains("location"))
    	    			.to("vm:LocationProcessing")	
    	    		.otherwise()
    					.to("vm:SDSProcessing")
    	    		.end();
    	        
    	    from("vm:ConsultantProcessing")
	    		.routeId("Process Consultant File")
	    		.log("Processing Consultant File")
	    		.unmarshal()
    			.bindy(BindyType.Csv, NHSConsultantEntities.class)
    			.split(body())
		    	.process(consultanttoFHIRPractitioner)
	    		.wireTap("activemq:Consultant")
	    		.end();
    	    
    	    from("vm:SDSProcessing")
    	    	.routeId("Prcess SDS/ODS File")
    	    	.log("Processing SDS/ODS File")
	    	    .unmarshal()
				.bindy(BindyType.Csv, NHSEntities.class)
				.split(body())
				.process(nhsEntitiestoFHIRResource)
				.wireTap("activemq:SDSResource")
				.end();
    	    
    	    from("vm:LocationProcessing")
	    		.routeId("Process Location File")
	    		.log("Processing Location File")
	    		.unmarshal()
				.bindy(BindyType.Csv, NHSTrustLocationEntities.class)
				.split(body())
				// Converts entity to FHIR
				.process(trustLocationEntitiestoFHIRLocation)
				.wireTap("activemq:Location")
				.end();
			
    	    from("activemq:Consultant")
		    	.routeId("FHIR Practitioner (Consultant)")
		    	.choice()
					.when(header("FHIROrganisationCode").isNotNull())
						.enrich("vm:lookupOrganisation",consultantEnrichwithOrganisation)
				.end()
		    	.enrich("vm:lookupResource",enrichUpdateType)
		    	.filter(header(Exchange.HTTP_METHOD)
	    	    	.isEqualTo("POST"))
	    	    		.to("vm:Update")
	    	    	.end()
	    	    .filter(header(Exchange.HTTP_METHOD)
	    	    	.isEqualTo("PUT"))
	    	    		.to("vm:Update")
	    	    	.end();
	    	    
    	    from("activemq:Location")
		    	.routeId("FHIR Location")
		    	.choice()
					.when(header("FHIROrganisationCode").isNotNull())
						.enrich("vm:lookupOrganisation",enrichLocationwithOrganisation)
				.end()
		    	.choice()
					.when(header("FHIRLocation").isNotNull())
						.enrich("vm:lookupLocation",enrichLocationwithLocation)
				.end()
		    	.enrich("vm:lookupResource",enrichUpdateType)
		    	.filter(header(Exchange.HTTP_METHOD)
	    	    	.isEqualTo("POST"))
	    	    		.to("vm:Update")
	    	    	.end()
	    	    .filter(header(Exchange.HTTP_METHOD)
	    	    	.isEqualTo("PUT"))
	    	    		.to("vm:Update")
	    	    		.to("vm:FileFHIR")
	    	    	.end();
	
    	    
    	    from("activemq:SDSResource")
    	    	.routeId("Process SDS Resource")
    	    	.choice()
					.when(header("FHIROrganisationCode").isNotNull())
						.enrich("vm:lookupOrganisation",enrichOrg)
				.end()
				.log("Lookup Resource")
    	    	.enrich("vm:lookupResource",enrichUpdateType)
    	    	.log("Update type ${header.CamelHttpMethod} ${header.CamelHttpPath} ${header.CamelHttpQuery}  ")
    	    	.filter(header(Exchange.HTTP_METHOD)
    	    		.isEqualTo("POST"))
    	    		.to("vm:Update")
    	    	.end()
    	    	.filter(header(Exchange.HTTP_METHOD)
    	    		.isEqualTo("PUT"))
    	    		.to("vm:Update")
    	    	.end();
    	    	// Gets are discarded
    	    
    	    from("vm:Update")
    	    	.routeId("Update JPA Server")
    	    	.setHeader(Exchange.HTTP_PATH, simple("${header.FHIRResource}",String.class))
    	    	.setHeader(Exchange.HTTP_QUERY,simple("_format=xml",String.class))
		    	.log("Update type ${header.CamelHttpMethod} ${header.CamelHttpPath} ${header.CamelHttpQuery} Record Entity ID = ${header.FHIROrganisationCode} partOf ${header.FHIROrganisationCode}")
		    	.setHeader("Prefer", simple("return=representation",String.class))
		    	.to("log:uk.co.mayfieldis.esb.SDSHAPI.SDSCamelRoute?level=INFO&showBody=true&showHeaders=true")
		    	.to("activemq:HAPIFHIR");
		    	/*
		    	.choice()
	    		.when(header(Exchange.FILE_NAME).isEqualTo("egpam.csv"))
	    			// only send updates for amendment load not a bulk load
	    			.to(env.getProperty("Internal.Amendments"));
	    		*/
    	    	
    	    from("vm:lookupOrganisation")
    	    	.routeId("Lookup FHIR Organisation")
    	    	.setBody(simple(""))
    	    	.setHeader(Exchange.HTTP_METHOD, simple("GET", String.class))
    	    	.setHeader(Exchange.HTTP_PATH, simple("/Organization",String.class))
		    	.setHeader(Exchange.HTTP_QUERY,simple("identifier="+FHIRCodeSystems.URI_NHS_OCS_ORGANISATION_CODE+"|${header.FHIROrganisationCode}",String.class))
		    	.to("vm:HAPIFHIR");
    	    
    	    from("vm:lookupLocation")
		    	.routeId("Lookup FHIR Location")
		    	//.log("Lookup Location ${header.FHIRLocation}")
		    	.setBody(simple(""))
		    	.setHeader(Exchange.HTTP_METHOD, simple("GET", String.class))
		    	.setHeader(Exchange.HTTP_PATH, simple("/Location",String.class))
		    	.setHeader(Exchange.HTTP_QUERY,simple("identifier="+TrustFHIRSystems.geturiNHSOrgLocation()+"|${header.FHIRLocation}",String.class))
		    	.to("vm:HAPIFHIR");
    	    
    	    from("vm:lookupResource")
		    	.routeId("Lookup FHIR Resources")
		    	.setBody(simple(""))
		    	.setHeader(Exchange.HTTP_METHOD, simple("GET", String.class))
		    	.setHeader(Exchange.HTTP_PATH, simple("${header.FHIRResource}",String.class))
		    	.setHeader(Exchange.HTTP_QUERY,simple("${header.FHIRQuery}",String.class))
		    	.to("vm:HAPIFHIR");
		    
    	    from("vm:HAPIFHIR")
				.routeId("HAPI FHIR")
				.to(env.getProperty("HAPIFHIR.Server"));
    	
	    	from("activemq:HAPIFHIR")
				.routeId("HAPI FHIR MQ")
				.to(env.getProperty("HAPIFHIR.Server"));
	    	    
    	    from("vm:FileFHIR")
    			.routeId("FileStore")
    			.to(env.getProperty("HAPIFHIR.FileStore")+"${date:now:yyyyMMdd hhmm.ss} ${header.CamelHL7MessageControl}.xml");
    }
}
