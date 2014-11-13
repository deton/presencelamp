// http://stackoverflow.com/questions/15841767/how-to-authenticate-ews-java-api
// http://ameblo.jp/softwaredeveloper/entry-11603208423.html
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import microsoft.exchange.webservices.data.*;

public class ExchangeClient {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: ExchangeClient <server> <email> <password> <roomemail>");
            System.out.println("   ex: ExchagneClient exchange.example.com taro@example.com p@sSw0rD room-00309@example.com");
            return;
        }
        ExchangeClient ec = new ExchangeClient();
        ec.outputAppointments(args[0], args[1], args[2], args[3]);
    }

    public void outputAppointments(String server, String userId, String password, String roomAddress) throws Exception {
        FindItemsResults<Appointment> findResults = getAppointments(server, userId, password, roomAddress);
        for (Appointment a : findResults.getItems()) {
            System.out.println("Start: " + a.getStart());
            System.out.println("End: " + a.getEnd());
            System.out.println("Subject: " + a.getSubject());
            System.out.println("Location: " + a.getLocation());
        }
    }

    public FindItemsResults<Appointment> getAppointments(String server, String userId, String password, String roomAddress) throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        Date startDate = cal.getTime();
        cal.add(Calendar.DATE, 1);
        Date endDate = cal.getTime();
        /*
        long now = System.currentTimeMillis();
        final long dayInMillis = 24 * 60 * 60 * 1000;
        Date startDate = new Date(now - dayInMillis);
        Date endDate = new Date(now + dayInMillis);
        */
        return getAppointments(server, userId, password, roomAddress, startDate, endDate);
    }

    public FindItemsResults<Appointment> getAppointments(String server, String userId, String password, String roomAddress, Date startDate, Date endDate) throws Exception {
        String serverUrl = "https://" + server + "/EWS/Exchange.asmx";
        ExchangeCredentials credentials = new WebCredentials(userId, password);
        ExchangeVersion exchangeVersion = ExchangeVersion.Exchange2010_SP2;
        ExchangeService exchangeService = new ExchangeService(exchangeVersion);
         
        exchangeService.setUrl(new URI(serverUrl));
        exchangeService.setCredentials(credentials);

        // 会議室の予定を取得する
        Mailbox room = new Mailbox(roomAddress);
        FolderId fid = new FolderId(WellKnownFolderName.Calendar, room);
        CalendarFolder cf = CalendarFolder.bind(exchangeService, fid);
        FindItemsResults<Appointment> findResults = cf.findAppointments(new CalendarView(startDate, endDate));
        return findResults;
    }
}
