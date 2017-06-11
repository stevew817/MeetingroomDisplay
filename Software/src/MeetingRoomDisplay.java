import java.util.List;
import microsoft.exchange.webservices.data.property.complex.availability.CalendarEvent;

public class MeetingRoomDisplay {
	String name;
	String emailAddress;
	String endpoint;
	List<CalendarEvent> eventList;
	Boolean updateRequired = false;
	
	MeetingRoomDisplay(String name, String emailAddress, String endpoint) {
		this.name = name;
		this.emailAddress = emailAddress;
		this.endpoint = endpoint;
	}
}
