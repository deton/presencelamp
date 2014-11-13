package main

import (
	"flag"
	"fmt"
	"net/http"
	"io/ioutil"
	"encoding/json"
	"log"
	"log/syslog"
	"os"
	"os/signal"
	"syscall"
	"time"
	"github.com/aqua/raspberrypi/gpio"
)

var baseurl = flag.String("url", "http://10.25.254.23:8080/MeetingRoomServlet/meetingroom", "URL of MeetingRoomServlet")
var room = flag.String("room", "room001@example.com", "room address")
var pinred = flag.Uint("pinred", 165, "GPIO pin for red LED")
var pinyellow = flag.Uint("pinyellow", 15, "GPIO pin for yellow LED")

type Command struct {
	State string
	Leftms int
	Waitms int
}

const colorTimerDur = 3 * 5 * 60 * 1000 // [ms]
//DEBUG const colorTimerDur = 30000 * 3
const blinkDurAtBegin = 2000
const blinkDurAtEnd = 50
// y1=ax1+b, y2=ax2+b
// x1=colorTimerDur, x2=0, y1=blinkDurAtBegin, y2=blikDurAtEnd
const a = float32(blinkDurAtBegin - blinkDurAtEnd)/colorTimerDur
const b = blinkDurAtEnd

var url string
var ledred, ledyellow *gpio.GPIOLine
var cmdch chan *Command

func main() {
	flag.Parse()
	url = fmt.Sprintf("%s?room=%s", *baseurl, *room)

	var err error
	var logger *syslog.Writer
	logger, err = syslog.New(syslog.LOG_NOTICE|syslog.LOG_USER, "presencelamp")
	if err != nil {
		panic(err)
	}
	log.SetOutput(logger)

        ledred, err = gpio.NewGPIOLine(*pinred, gpio.OUT)
        if err != nil {
                log.Printf("Error setting up GPIO %v: %v", *pinred, err)
                return
        }
	defer ledred.Close()
	defer ledred.SetState(false)

        ledyellow, err = gpio.NewGPIOLine(*pinyellow, gpio.OUT)
        if err != nil {
                log.Printf("Error setting up GPIO %v: %v", *pinyellow, err)
                return
        }
	defer ledyellow.Close()
	defer ledyellow.SetState(false)

	sigch := make(chan os.Signal)
	signal.Notify(sigch, os.Interrupt, syscall.SIGTERM)
	go func() {
		<-sigch
		ledred.SetState(false)
		ledyellow.SetState(false)
		os.Exit(0)
	}()

	cmdch = make(chan *Command)
	go httpreqloop()

	// indicate successful startup
	ledred.SetState(true)
	time.Sleep(500 * time.Millisecond)
	ledred.SetState(false)
	ledblinkloop()
}

func httpreqloop() {
	retryWait := 1
	for {
		cmd, err := httpreq()
		if err != nil {
			log.Printf("http error %v (retry=%d)\n", err, retryWait)
			// retry after short sleep (1,2,4,5[min]. max 5[min])
			time.Sleep(time.Duration(retryWait) * time.Minute)
			retryWait *= 2
			if retryWait > 5 {
				// avoid blinking forever
				cmd = &Command{State:"NONE"}
				cmdch <- cmd
				retryWait = 5
			}
			continue
		}
		log.Println("http got", cmd)
		cmdch <- cmd
		waitms := cmd.Waitms
		if waitms <= 0 {
			waitms = 5 * 60 * 1000
		}
		time.Sleep(time.Duration(waitms) * time.Millisecond)
		retryWait = 1
	}
}

func httpreq() (*Command, error) {
	//log.Println("http getting", url)
	resp, err := http.Get(url)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	body, err := ioutil.ReadAll(resp.Body)
	var cmd Command
	if err := json.Unmarshal(body, &cmd); err != nil {
		return nil, err
	}
	return &cmd, nil
}

var blinkfunc func()
var ledon = false
var prevTime time.Time
var leftms = 0
var blinkdur = 0

func shortenBlinkDur() {
	now := time.Now()
	spent := now.Sub(prevTime)
	prevTime = now
	leftms -= int(spent/time.Millisecond)
	if leftms < 0 {
		leftms = 0
	}
	blinkdur = int(float32(leftms) * a) + b
	//DEBUG log.Println(leftms, blinkdur, spent)
}

func colortimer0() {
	if ledon {
		ledyellow.SetState(true)
	} else {
		ledyellow.SetState(false)
	}
	ledon = !ledon
	time.Sleep(time.Duration(blinkdur) * time.Millisecond)
	shortenBlinkDur()
}

func colortimer2() {
	if ledon {
		ledred.SetState(false)
		ledyellow.SetState(true)
	} else {
		ledred.SetState(true)
		ledyellow.SetState(false)
	}
	ledon = !ledon
	time.Sleep(time.Duration(blinkdur) * time.Millisecond)
	shortenBlinkDur() // make blinkdur shorter
}

func colortimerReminderWait() {
	ledred.SetState(true)
	ledyellow.SetState(false)
	time.Sleep(time.Duration(blinkdur) * time.Millisecond)
	blinkdur = 50
	blinkfunc = colortimerReminderBlink
}

func colortimerReminderBlink() {
	for i := 0; i < 10; i++ {
		if ledon {
			ledred.SetState(true)
		} else {
			ledred.SetState(false)
		}
		ledon = !ledon
		time.Sleep(time.Duration(blinkdur) * time.Millisecond)
	}
	blinkdur = 3000
	blinkfunc = colortimerReminderWait
}

func newstatus(cmd *Command) {
	leftms = cmd.Leftms
	prevTime = time.Now()
	switch cmd.State {
	case "COLORTIMER0", "COLORTIMER1":
		blinkdur = int(float32(leftms) * a) + b
		//DEBUG log.Println(leftms, blinkdur, a, b)
		blinkfunc = colortimer0
	case "COLORTIMER2":
		blinkdur = int(float32(leftms) * a) + b
		blinkfunc = colortimer2
	case "COLORTIMER_REMINDER":
		blinkdur = 3000
		blinkfunc = colortimerReminderWait
	case "NONE":
		fallthrough
	default:
		blinkfunc = nonefunc
	}
}

func nonefunc() {
	ledred.SetState(false)
	ledyellow.SetState(false)
	cmd := <-cmdch // block
	newstatus(cmd)
}

func ledblinkloop() {
	blinkfunc = nonefunc
	for {
		select {
		case cmd := <-cmdch:
			newstatus(cmd)
		default:
			blinkfunc()
		}
	}
}
