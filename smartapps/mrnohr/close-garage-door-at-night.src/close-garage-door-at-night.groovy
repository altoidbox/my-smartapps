/**
 *  Close Garage Door At Night
 *
 *  Copyright 2014 Matt Nohr
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
/**
 * Double check your garage door. First warn if it is still open, and then always close when switching
 * to a specific mode. For example, send a notification at 8:00 PM if the garage is still open, and
 * then close it when switching to "overnight" mode. Also supports automatically closing if the door is
 * left open past a certain time.
 */
definition(
		name: "Close Garage Door At Night",
		namespace: "mrnohr",
		author: "Matt Nohr",
		description: "If the garage door is open, close it",
		category: "My Apps",
		iconUrl: "https://dl.dropboxusercontent.com/u/2256790/smartapp-icons/garage%402x.jpg",
		iconX2Url: "https://dl.dropboxusercontent.com/u/2256790/smartapp-icons/garage%402x.jpg")

preferences {
	section("What Garage Door?") {
		input "garageDoor", "capability.garageDoorControl", title: "Garage Door"
	}
	section("Actions") {
		input "alertTime", "time", title: "Alert Me at This Time", required: false
		input "closeMode", "mode", title: "Close when switching to this mode", required: false
		input "closeTime", "time", title: "Close Door at This Time", required: false
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unschedule()
	unsubscribe()
	initialize()
}

def initialize() {
	if(closeMode) {
		subscribe(location, "modeChangeHandler")
	}
	if(alertTime) {
		runDaily(alertTime, "checkAndAlert")
	}
	if(closeTime) {
		runDaily(closeTime, "checkAndClose")
	}
}

def checkAndAlert() {
	def doorOpen = isDoorOpen(checkAndAlert)
	log.debug "In checkAndAlert - is the door open: $doorOpen"
	if(doorOpen) {
		def message = "The garage door is still open!"
		log.info message
		sendNotification(message, [method: "push"])
	}
}

def modeChangeHandler(evt) {
	log.debug "In modeChangeHandler for $evt.name = $evt.value"
	if(evt.value == closeMode && isDoorOpen(checkAndClose)) {
		def message = "Closing garage door since it was left open"
		log.info message
		sendNotification(message, [method: "push"])

		garageDoor.close()
	}
}

def checkAndClose() {
	def doorOpen = isDoorOpen(checkAndClose)
	log.debug "In checkAndClose - is the door open: $doorOpen"
	if(doorOpen) {
		def message = "Closing garage door since it was left open past close time"
		log.info message
		sendNotification(message, [method: "push"])
		
		garageDoor.close()
	}
}

def isDoorOpen(rescheduleMethod=null) {
	def doorState = garageDoor.currentState("door")
	if(doorState?.value == "open") {
		def thresholdMinutes = 5 //If the door's been open for less than 5 minutes, don't report as open
		def recheduleDelay = 20 //Given that condition, check again in this number of minutes
		def doorOpenMs = now() - doorState.date.time
		if(doorOpenMs > thresholdMinutes * 60 * 1000) {
			return true
		} else if(rescheduleMethod != null) {
			log.debug "Door was opened ${doorOpenMs / (60*1000)} minutes ago, deferring $recheduleDelay minutes"
			runIn(recheduleDelay*60, rescheduleMethod)
		} else {
			def message = "Garage door was opened ${doorOpenMs / (60*1000)} minutes ago, rescheduling not allowed"
			log.info message
			sendNotification(message, [method: "push"])
		}
	}
	return false
}
