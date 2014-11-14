import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Date;
import java.util.Properties;
import java.util.logging.*;
import javax.servlet.*;
import javax.servlet.annotation.*;
import javax.servlet.http.*;
import microsoft.exchange.webservices.data.*;

@WebServlet(name="MeetingRoomServlet",urlPatterns={"/meetingroom"})
public class MeetingRoomServlet extends HttpServlet {
    final static long WAITMS_DEFAULT = 5 * 60 * 1000; // 5[min]
    final static long DIFF_FAR =      20 * 60 * 1000;
    final static long DIFF_STAGE0 =   15 * 60 * 1000;
    final static long DIFF_STAGE1 =   10 * 60 * 1000;
    final static long DIFF_STAGE2 =    5 * 60 * 1000;
    final static long DIFF_REMINDER = -5 * 60 * 1000;

    enum State {
        NONE, COLORTIMER0, COLORTIMER1, COLORTIMER2, COLORTIMER_REMINDER,
    }

    static Logger logger = Logger.getLogger("MeetingRoomServlet");
    ExchangeClient exchange = new ExchangeClient();

    final static String server = LocalProperties.server;
    final static String userId = LocalProperties.userId;
    final static String password = LocalProperties.password;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try (PrintWriter out = resp.getWriter()) {
            String room = req.getParameter("room");
            ResponseParams resparam = getStateByAppointment(room);

            resp.setContentType("application/json");
            out.print("{\"state\":\"");
            switch (resparam.state) {
            case COLORTIMER0:
                out.print("COLORTIMER0");
                break;
            case COLORTIMER1:
                out.print("COLORTIMER1");
                break;
            case COLORTIMER2:
                out.print("COLORTIMER2");
                break;
            case COLORTIMER_REMINDER:
                out.print("COLORTIMER_REMINDER");
                break;
            case NONE:
            default:
                out.print("NONE");
                break;
            }
            out.print("\",\"leftms\":" + resparam.leftms
                    +   ",\"waitms\":" + resparam.waitms + "}");
        } catch (Exception ex) {
            try {
                resp.sendError(resp.SC_INTERNAL_SERVER_ERROR);
            } catch (IOException ioex) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "on sendError()", ioex);
                }
            }
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "doGet", ex);
                if (ex instanceof ServletException) {
                    logger.log(Level.WARNING, "rootCause",
                            ((ServletException)ex).getRootCause());
                }
            }
        }
    }

    class ResponseParams {
        State state;
        long leftms;
        long waitms; // wait ms for next access
        public ResponseParams(State state, long leftms, long waitms) {
            this.state = state;
            this.leftms = leftms;
            this.waitms = waitms;
        }
    }

    ResponseParams getStateByAppointment(String room) throws ServiceLocalException {
        Appointment a = getNearestAppointment(room);
        if (a == null) {
            return new ResponseParams(State.NONE, 0, WAITMS_DEFAULT);
        }
        Date start = a.getStart();
        long diff = start.getTime() - System.currentTimeMillis();
        if (diff > DIFF_FAR) {
            return new ResponseParams(State.NONE, diff, WAITMS_DEFAULT);
        } else if (diff > DIFF_STAGE0) {
            return new ResponseParams(State.NONE, diff, diff - DIFF_STAGE0);
        } else if (diff > DIFF_STAGE1) {
            return new ResponseParams(State.COLORTIMER0, diff, diff - DIFF_STAGE1);
        } else if (diff > DIFF_STAGE2) {
            return new ResponseParams(State.COLORTIMER1, diff, diff - DIFF_STAGE2);
        } else if (diff > 0) {
            return new ResponseParams(State.COLORTIMER2, diff, diff);
        } else if (diff > DIFF_REMINDER) {
            return new ResponseParams(State.COLORTIMER_REMINDER, diff, diff - DIFF_REMINDER);
        } else { // diff <= -5
            return new ResponseParams(State.NONE, diff, WAITMS_DEFAULT);
        }
    }

    Appointment getNearestAppointment(String room) {
        // get near appointments only
        long now = System.currentTimeMillis();
        Date startDate = new Date(now - (DIFF_FAR + WAITMS_DEFAULT));
        Date endDate = new Date(now + (DIFF_FAR + WAITMS_DEFAULT));
        FindItemsResults<Appointment> findResults;
        try {
            findResults = exchange.getAppointments(server, userId, password, room, startDate, endDate);
        } catch (Exception ex) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "getAppointments", ex);
            }
            return null;
        }
        Appointment nearest = null;
        long diff;
        long mindiff = Long.MAX_VALUE;
        for (Appointment a : findResults.getItems()) {
            Date start;
            try {
                start = a.getStart();
            } catch (ServiceLocalException ex) {
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "getStart", ex);
                }
                continue;
            }
            diff = Math.abs(start.getTime() - now);
            if (diff < mindiff) {
                nearest = a;
                mindiff = diff;
            }
        }
        return nearest;
    }
}
