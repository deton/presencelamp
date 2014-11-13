package main
import (
	"fmt"
	"net/http"
)

type Hello struct{}

var resp = []string{
	`{"state": "COLORTIMER0", "leftms": 90000, "waitms": 30000}`,
	`{"state": "COLORTIMER1", "leftms": 60000, "waitms": 30000}`,
	`{"state": "COLORTIMER2", "leftms": 30000, "waitms": 30000}`,
	`{"state": "COLORTIMER_REMINDER", "waitms": 30000}`,
	`{"state": "NONE", "waitms": 30000}`,
}

var count = 0

func (h Hello) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	fmt.Fprint(w, resp[count])
	count++
	if count >= len(resp) {
		count = 0
	}
}

func main() {
	var h Hello
	http.ListenAndServe("localhost:8080", h)
}

