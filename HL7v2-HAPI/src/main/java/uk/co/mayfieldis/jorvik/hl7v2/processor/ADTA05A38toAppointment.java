package uk.co.mayfieldis.jorvik.hl7v2.processor;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;


import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.hl7.fhir.dstu3.model.Appointment;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Location;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Bundle.BundleType;
import org.hl7.fhir.dstu3.model.Bundle.HTTPVerb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import uk.co.mayfieldis.jorvik.core.FHIRConstants.FHIRCodeSystems;
import uk.co.mayfieldis.jorvik.core.FHIRConstants.NHSTrustFHIRCodeSystems;

public class ADTA05A38toAppointment implements Processor {

	private static final Logger log = LoggerFactory.getLogger(uk.co.mayfieldis.jorvik.hl7v2.processor.ADTA05A38toAppointment.class);
	
	Terser terser = null;
	
	public ADTA05A38toAppointment(FhirContext ctx, Environment env, NHSTrustFHIRCodeSystems TrustFHIRSystems)
	{
		this.ctx = ctx;
		this.env = env;
		this.TrustFHIRSystems = TrustFHIRSystems;
	}
	
	private NHSTrustFHIRCodeSystems TrustFHIRSystems;
	
	public Environment env;
	
	public FhirContext ctx;
	
	private String terserGet(String query)
	{
		String result = "";
		try
		{
			result = terser.get(query);
			//log.info(query+" = "+result);
		}
		catch(HL7Exception hl7ex)
		{
			// Could add some extra code here
			
			log.debug("#1 "+hl7ex.getMessage());
		}
		catch(Exception ex)
		{
			// Exception thrown on no data
			log.debug("#2 "+ex.getMessage());
		}
		
		return result;
	}
	@Override
	public void process(Exchange exchange) throws Exception {
		
		Message message = exchange.getIn().getBody(Message.class);
		
		Appointment appointment= new Appointment();
		Patient patient = new Patient();
		patient.setId(IdDt.newRandomUuid());
		Identifier patientid = new Identifier();
		Bundle bundle = new Bundle();
		
		bundle.setType(BundleType.TRANSACTION);
		// Use Terser as code is more readable
		terser = new Terser(message);
		
		Integer maxRepitions = 5;
		
		try
		{
			// Identifiers PID.PatientIdentifierList()
			for (int f=0;f<maxRepitions;f++)
			{
				String code =null;
				String value =null;
				if (f==0)
				{
					code =terserGet("/.PID-3-4");
					value =terserGet("/.PID-3-1");		
				}
				else
				{
					code =terserGet("/.PID-3("+f+")-4");
					value =terserGet("/.PID-3("+f+")-1");
				}
			
				if (code != null && !code.isEmpty())
				{
					log.debug("PID "+code+" "+value);
					switch (code)
					{
						case "PAS":
							if (exchange.getIn().getHeader("FHIRPatient") ==null || exchange.getIn().getHeader("FHIRPatient").toString().isEmpty())
							{
								exchange.getIn().setHeader("FHIRPatient",env.getProperty("ORG.PatientIdentifier"+code)+"|"+value);
								patientid
									.setValue(value)
									.setSystem(env.getProperty("ORG.PatientIdentifier"+code));
							}
							break;
						case "NHS":
							if (exchange.getIn().getHeader("FHIRPatient") ==null || exchange.getIn().getHeader("FHIRPatient").toString().isEmpty())
							{
								exchange.getIn().setHeader("FHIRPatient",env.getProperty("ORG.PatientIdentifier"+code)+"|"+value);
								patientid
									.setValue(value)
									.setSystem(env.getProperty("ORG.PatientIdentifier"+code));
							}
							break;
						default:
							exchange.getIn().setHeader("FHIRPatient",env.getProperty("ORG.PatientIdentifier"+code)+"|"+value);
							patientid
								.setValue(value)
								.setSystem(env.getProperty("ORG.PatientIdentifier"+code));
							break;
					}
				}
			}
			// Names PID.PatientName
			log.debug("FHIRPatient  = "+exchange.getIn().getHeader("FHIRPatient").toString());
			patient.addIdentifier(patientid);
			
			if (terserGet("/.PV1-19-1") != null && !terserGet("/.PV1-19-1").isEmpty())
			{
				appointment.addIdentifier()
					.setSystem(TrustFHIRSystems.geturiNHSOrgAppointmentId())
					.setValue(terserGet("/.PV1-19-1"));
			}
			switch (terserGet("/.MSH-9-2"))
			{
				case "A05":
					appointment.setStatus(Appointment.AppointmentStatus.BOOKED);
					break;
				case "A38":
					appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
					break;
			}
			// StartDate
			if (terserGet("/.PV1-44-1") != null && !terserGet("/.PV1-44-1").isEmpty())
			{
				SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMddHHmmss");
				
	        	try {
	        		Date date;
	        		String tempDate = terserGet("/.PV1-44-1");
	        		if (tempDate.length()==12)
	        		{
	        			tempDate = tempDate+"00";
	        		}
	        		date = fmt.parse(tempDate);
	        		
	        		appointment.setStart(date);
	        	} catch (ParseException e1) {
	        	// TODO Auto-generated catch block
	        	}
			}
			else
			{
				// Assume it's a proposed appointment
				appointment.setStatus(Appointment.AppointmentStatus.PROPOSED);
			}
			if (terserGet("/.PV1-45-1") != null && !terserGet("/.PV1-45-1").isEmpty())
			{
				SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMddHHmmss");
				
	        	try {
	        		Date date;
	        		String tempDate = terserGet("/.PV1-45-1");
	        		if (tempDate.length()==12)
	        		{
	        			tempDate = tempDate+"00";
	        		}
	        		date = fmt.parse(tempDate);
	        		appointment.setEnd(date);
	        		appointment.setStatus(Appointment.AppointmentStatus.FULFILLED);
	        	} catch (ParseException e1) {
	        	// TODO Auto-generated catch block
	        	}
			}
			
			
			if (terserGet("/.PV1-10-1") != null && !terserGet("/.PV1-10-1").isEmpty())
			{
				CodeableConcept type = new CodeableConcept();
				type.addCoding()
					.setSystem(FHIRCodeSystems.URI_NHS_SPECIALTIES)
					.setCode(terserGet("/.PV1-10-1"));
				
				appointment.addServiceType(type);
			}
			
			
			if (terserGet("/.PV1-3-1") != null && !terserGet("/.PV1-3-1").isEmpty())
			{
				//exchange.getIn().setHeader("FHIRLocation", terserGet("/.PV1-3-1").replace(' ','-'));
				Location parentLoc = new Location();
				parentLoc.setId(IdDt.newRandomUuid());
				parentLoc.addIdentifier()
					.setValue(terserGet("/.PV1-3-1").replace(' ', '-'))
					.setSystem(TrustFHIRSystems.geturiNHSOrgLocation());
				
				// Create reference in main resource
				appointment.addParticipant().setActor(new Reference(parentLoc.getId()));
				
				bundle.addEntry()
				   .setFullUrl(parentLoc.getId())
				   .setResource(parentLoc)
				   .getRequest()
				      .setUrl("Location")
				      .setIfNoneExist("Location?identifier="+parentLoc.getIdentifier().get(0).getSystem()+"|"+parentLoc.getIdentifier().get(0).getValue())
				      .setMethod(HTTPVerb.POST);
			}
			
			
			if (terserGet("/.PV1-19-1") != null && !terserGet("/.PV1-19-1").isEmpty())
			{
				exchange.getIn().setHeader("FHIRAppointment", terserGet("/.PV1-19-1"));
				exchange.getIn().setHeader("FHIREncounter", terserGet("/.PV1-19-1"));
			}
			
			
			
			if (terserGet("/.PV1-9-1") != null && !terserGet("/.PV1-9-1").isEmpty())
			{
				//exchange.getIn().setHeader("FHIRPractitioner", terserGet("/.PV1-9-1"));
				Practitioner consultant = new Practitioner();
				consultant.setId(IdDt.newRandomUuid());
				consultant.addIdentifier()
					.setValue(terserGet("/.PV1-9-1"))
					.setSystem(FHIRCodeSystems.URI_OID_NHS_PERSONNEL_IDENTIFIERS);
				
				appointment.addParticipant()
					.setActor(new Reference(consultant.getId()))
					.addType()
						.addCoding()
							.setCode("CON")
							.setSystem("http://hl7.org/fhir/v3/ParticipationType");
				
				bundle.addEntry()
				   .setFullUrl(consultant.getId())
				   .setResource(consultant)
				   .getRequest()
				      .setUrl("Practitioner")
				      .setIfNoneExist("Practitioner?identifier="+consultant.getIdentifier().get(0).getSystem()+"|"+consultant.getIdentifier().get(0).getValue())
				      .setMethod(HTTPVerb.POST);
			}
			
			
			appointment.addParticipant()
				.setActor(new Reference(patient.getId()))
				.addType()
					.addCoding()
						.setCode("ATND")
						.setSystem("http://hl7.org/fhir/v3/ParticipationType");
			bundle.addEntry()
			   .setFullUrl(patient.getId())
			   .setResource(patient)
			   .getRequest()
			      .setUrl("Patient")
			      .setIfNoneExist("Patient?identifier="+patient.getIdentifier().get(0).getSystem()+"|"+patient.getIdentifier().get(0).getValue())
			      .setMethod(HTTPVerb.POST);
			
			// Master resource
			bundle.addEntry()
			   .setResource(appointment)
			   .getRequest()
			      .setUrl("Appointment?identifier="+appointment.getIdentifier().get(0).getSystem()+"|"+appointment.getIdentifier().get(0).getValue())
			      .setMethod(HTTPVerb.PUT);
			
			String Response = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(bundle);
			
			exchange.getIn().setHeader("FHIRResource","/");
			exchange.getIn().setHeader(Exchange.HTTP_METHOD, "POST");
			exchange.getIn().setBody(Response);
			
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
			log.error("#3 "+ exchange.getExchangeId() + " "  + ex.getMessage() +  " "  + ex.getStackTrace() 
					+" Properties: " + exchange.getProperties().toString()
					+" Headers: " + exchange.getIn().getHeaders().toString() 
					+ " Message:" + exchange.getIn().getBody().toString());
		}
		exchange.getIn().setHeader("FHIRResource", "Appointment");
	
		exchange.getIn().setHeader(Exchange.HTTP_QUERY,"");
		exchange.getIn().setHeader(Exchange.CONTENT_TYPE,"application/xml+fhir");
		
	}

}
