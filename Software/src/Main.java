import java.net.InetSocketAddress;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.*;

import microsoft.exchange.webservices.data.core.*;
import microsoft.exchange.webservices.data.core.enumeration.availability.AvailabilityData;
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion;
import microsoft.exchange.webservices.data.core.enumeration.misc.error.ServiceError;
import microsoft.exchange.webservices.data.core.response.AttendeeAvailability;
import microsoft.exchange.webservices.data.credential.*;
import microsoft.exchange.webservices.data.misc.availability.AttendeeInfo;
import microsoft.exchange.webservices.data.misc.availability.GetUserAvailabilityResults;
import microsoft.exchange.webservices.data.misc.availability.TimeWindow;
import microsoft.exchange.webservices.data.property.complex.availability.CalendarEvent;
import microsoft.exchange.webservices.data.property.complex.availability.Suggestion;
import microsoft.exchange.webservices.data.property.complex.availability.TimeSuggestion;

import com.arm.mbed.restclient.*;
import com.arm.mbed.restclient.endpoint.EndpointResourceTarget;
import com.arm.mbed.restclient.endpoint.EndpointTarget;
import com.arm.mbed.restclient.endpoint.Entity;
import com.arm.mbed.restclient.entity.Endpoint;
import com.arm.mbed.restclient.entity.ResourceDescription;
import com.arm.mbed.restclient.entity.notification.EndpointDescription;
import com.arm.mbed.restclient.entity.notification.ResourceNotification;

public class Main {
	private static ArrayList<MeetingRoomDisplay> roomlist = new ArrayList<MeetingRoomDisplay>();

	//Change to own Exchange server URL if not using Office365
	private static final String EXCHANGE_SERVER = "https://outlook.office365.com/EWS/Exchange.asmx";
	private static final String EXCHANGE_USERNAME = "<username>";
	private static final String EXCHANGE_PASSWORD = "<password>";
	
	private static final String MBED_DS_SERVER = "https://api.connector.mbed.com";
	private static final String MBED_DS_TOKEN = "Bearer <token>";

	public static void main(String[] args) throws Exception {
		// Initialize list of nodes with associated name and exchange user
		roomlist.add(new MeetingRoomDisplay("Meeting room 1", "Confrm.1@contoso.onmicrosoft.com", "<mbed connector endpoint GUID>"));
		roomlist.add(new MeetingRoomDisplay("Meeting room 2", "Confrm.2@contoso.onmicrosoft.com", "<mbed connector endpoint GUID>"));
		// ... add more as required
		
		ExchangeService service = new ExchangeService(ExchangeVersion.Exchange2010_SP2);
		ExchangeCredentials credentials = new WebCredentials(EXCHANGE_USERNAME, EXCHANGE_PASSWORD);
		service.setCredentials(credentials);
		service.setUrl(new URI(EXCHANGE_SERVER));
		
		// Create a list of attendees for which to request availability
		// information and meeting time suggestions.

		List<AttendeeInfo> attendees = new ArrayList<AttendeeInfo>();
		for (MeetingRoomDisplay room : roomlist) {
			attendees.add(new AttendeeInfo(room.emailAddress));
		}

		SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");

		//minimum time frame allowed by API is 24 hours
		Calendar cal = Calendar.getInstance();
		Date start = formatter.parse(formatter.format(cal.getTime()));
		System.out.println(start.toString());
		cal.add(Calendar.DAY_OF_MONTH, 3);
		Date end = formatter.parse(formatter.format(cal.getTime()));
		System.out.println(end.toString());

		// Call the availability service.
		GetUserAvailabilityResults results = service.getUserAvailability(
		    attendees,
		    new TimeWindow(start, end),
		    AvailabilityData.FreeBusy);

		// Output attendee availability information.
		int attendeeIndex = 0;

		for (AttendeeAvailability attendeeAvailability : results.getAttendeesAvailability()) {
		    System.out.println("Availability for " + roomlist.get(attendeeIndex).name);
		    if (attendeeAvailability.getErrorCode() == ServiceError.NoError) {
				// Save events to that room's event list
		    	roomlist.get(attendeeIndex).eventList = new ArrayList<CalendarEvent>();
		        for (CalendarEvent calendarEvent : attendeeAvailability.getCalendarEvents()) {
		            System.out.println("Calendar event");
		            System.out.println("  Start time: " + calendarEvent.getStartTime().toString());
		            System.out.println("  End time: " + calendarEvent.getEndTime().toString());

		            if ( calendarEvent.getEndTime().after(Calendar.getInstance().getTime()) ) {
		            	roomlist.get(attendeeIndex).eventList.add(calendarEvent);
		            	System.out.println("added!");
		            }
		            if (calendarEvent.getDetails() != null)
		            {
		                System.out.println("  Subject: " + calendarEvent.getDetails().getSubject());
		            }
		        }
				if (roomlist.get(attendeeIndex).eventList == null) {
					roomlist.get(attendeeIndex).eventList = newList;
					roomlist.get(attendeeIndex).updateRequired = true;
				} else if (!roomlist.get(attendeeIndex).eventList.equals(newList)) {
					roomlist.get(attendeeIndex).eventList = newList;
					roomlist.get(attendeeIndex).updateRequired = true;
				}
		    }

		    attendeeIndex++;
		}		
		
		service.close();
		
		MbedClient client;
		URI uri = new URI(MBED_DS_SERVER);
		int port = 443;
		client = MbedClientBuilder.newBuilder().credentials(MBED_DS_TOKEN)
				.secure()
				.notifChannelLongPolling()
				.notifListener(new NotificationListenerImpl()).build(new InetSocketAddress(uri.getHost(), port));
		
		// Iterate over all endpoints in our Connector realm
		for (Endpoint ep : client.endpoints().readAll()) {
			System.out.println("EP: " + ep.toString());
			
			for(MeetingRoomDisplay room : roomlist) {
				// Match endpoint ID with a node from our list
				if (ep.getName().equals(room.endpoint) && room.updateRequired) {
					// Update display as needed
					room.updateRequired = false;
					EndpointTarget target = client.endpoint(ep.getName());
					
					String nowPeriod = "00:00 - 00:00";
					String nowOwner = "Free";
					String nextPeriod = "00:00 - 23:59";
					String nextOwner = "Free";
					
					if(!room.eventList.isEmpty()) {
						
						Calendar now = Calendar.getInstance();
						Calendar tom = Calendar.getInstance();
						tom.add(Calendar.DAY_OF_MONTH, 1);
						Date tomorrow = tom.getTime();
						
						//Check if first event is today
						CalendarEvent ev = room.eventList.get(0);
						if (ev.getStartTime().compareTo(tomorrow) < 0) {
							// Check if event is current
							if(ev.getStartTime().before(now.getTime()) && ev.getEndTime().after(now.getTime())) {
								nowPeriod = String.format("%02d:%02d - %02d:%02d", ev.getStartTime().getHours(), ev.getStartTime().getMinutes(), ev.getEndTime().getHours(), ev.getEndTime().getMinutes());
								nowOwner = ev.getDetails().getSubject();
								// Check if there is another event scheduled
								if (room.eventList.size() >= 2) {
									CalendarEvent next = room.eventList.get(1);
									if ( next.getStartTime().before(tomorrow)) {
										nextPeriod = String.format("%02d:%02d - %02d:%02d", next.getStartTime().getHours(), next.getStartTime().getMinutes(), next.getEndTime().getHours(), next.getEndTime().getMinutes());
										nextOwner = next.getDetails().getSubject();
									} else {
										nextPeriod = String.format("%02d:%02d - 23:59", ev.getEndTime().getHours(), ev.getEndTime().getMinutes());
									}
								} else {
									nextPeriod = String.format("%02d:%02d - 23:59", ev.getEndTime().getHours(), ev.getEndTime().getMinutes());
								}
							} else {
								nowPeriod = String.format("%02d:%02d - %02d:%02d", now.getTime().getHours(), now.getTime().getMinutes(), ev.getStartTime().getHours(), ev.getStartTime().getMinutes());
								nextPeriod = String.format("%02d:%02d - %02d:%02d", ev.getStartTime().getHours(), ev.getStartTime().getMinutes(), ev.getEndTime().getHours(), ev.getEndTime().getMinutes());
								nextOwner = ev.getDetails().getSubject();
							}
						}
					}
					
					for(ResourceDescription desc : target.readResourceList()) {
						System.out.println(desc.toString());
						if(desc.getResourceType() != null && desc.getResourceType().equals("RoomName")) {
							EndpointResourceTarget res = target.resource(desc.getUriPath());
							res.put(Entity.text(room.name));
							System.out.println("room");
						}
						if(desc.getResourceType() != null && desc.getResourceType().equals("CurrentOwner")) {
							EndpointResourceTarget res = target.resource(desc.getUriPath());
							res.put(Entity.text(nowOwner));
							System.out.println(nowOwner);
						}
						if(desc.getResourceType() != null && desc.getResourceType().equals("CurrentSlot")) {
							EndpointResourceTarget res = target.resource(desc.getUriPath());
							res.put(Entity.text(nowPeriod));
							System.out.println(nowPeriod);
						}
						if(desc.getResourceType() != null && desc.getResourceType().equals("NextOwner")) {
							EndpointResourceTarget res = target.resource(desc.getUriPath());
							res.put(Entity.text(nextOwner));
							System.out.println(nextOwner);
						}
						if(desc.getResourceType() != null && desc.getResourceType().equals("NextSlot")) {
							EndpointResourceTarget res = target.resource(desc.getUriPath());
							res.put(Entity.text(nextPeriod));
							System.out.println(nextPeriod);
						}
					}
				}
			}
		}
		Thread.sleep(2000);
		System.exit(0);
	}
	
	static class NotificationListenerImpl implements NotificationListener {

		NotificationListenerImpl() {
		}

		@Override
		public void onEndpointsRegistered(EndpointDescription[] endpoints) {
		}

		@Override
		public void onEndpointsUpdated(EndpointDescription[] endpoints) {
		}

		@Override
		public void onEndpointsRemoved(String[] endpointsRemoved) {
		}

		@Override
		public void onResourcesUpdated(ResourceNotification[] resourceNotifications) {
		}

		@Override
		public void onEndpointsExpired(String[] endpointsExpired) {
		}
	}

}
