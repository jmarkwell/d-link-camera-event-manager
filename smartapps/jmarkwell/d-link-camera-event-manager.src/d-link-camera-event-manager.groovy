/**
 *  D-Link Camera Event Manager
 *  Build 2020020602
 *
 *  Adapted from Ben Lebson's (GitHub: blebson) Smart Security Camera SmartApp that is designed to work with his D-Link series of device
 *  handlers.
 *
 *  Copyright 2020 Jordan Markwell
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 *  ChangeLog:
 *      
 *      20200206
 *          01: Updated snap() function conditionals to reflect that the photoLockTime state variable is now being used in non-home modes.
 *          02: snap() commands will now be delayed by burstDelay seconds in the case that video recording is enabled to ensure that both
 *              commands have enough time to process properly.
 *
 *      20200203
 *          01: Burst photos will now be executed as scheduled processes.
 *          02: Time delay between burst photo shots is now a user preference.
 *          03: Fixed a burst photo timing bug that was causing photos after the 2nd burst shot to fail.
 *          04: Fixed a burst photo bug that was causing photos to fail when multiple events occur at the same time.
 *          05: Duplicitous vrOn() commands will no longer be sent to the camera.
 *          06: Corrected an errant value reported in the log output of the eventHandler() function.
 *
 *      20200128
 *          01: Removed state.positionState as PTZPos can now be accessed as a device attribute.
 *
 *      20180618
 *          01: moveLock will no longer extend motion locking for the position it is currently in.
 *
 *      20180510
 *          01: Removed the moveLockOff() and moveLockHandler() functions.
 *          02: The attribute, "switch6", will now be known as, "PTZPos".
 *          03: The function, "sendMessage()", will now be known as, "eventHandler()".
 *          04: Code cleanup.
 *
 *      20171124
 *          01: Removed conditions in the moveLockOff() function.
 *          02: Added moveLockOff() calls in installed() and updated() functions.
 *
 *      20170906
 *          01: Increased photo burst delay to 8 seconds.
 *
 *      20170825
 *          01: Added debug logging setting.
 *
 *      20170824
 *          01: Functionized setting of moveDelay and de-functionized photoLockOff() by setting conditions on snap().
 *          02: Made the menus more fancy.
 *
 *      20170823
 *          01: Mode based reset didn't fix the problem with photoLock. Mode restrictions appear to exist in a higher level process.
 *              Converted photoLock from a delay based system to a time based system.
 *
 *      20170821
 *          01: If movement is enabled and the camera is not in the requested position for a photo, snap() will now be triggered by
 *              movement events from the camera.
 *          02: The lock, photoLock is instance specific and sometimes hangs on a mode change. Adding a mode change subscription that will
 *              reset the lock.
 *
 *      20170818
 *          01: Converted moveLock to a device attribute with corresponding functions so that the lock works in conjunction with other
 *              instances of D-Link Camera Event Manager.
 *
 *      Earlier:
 *          Dwell time following a motion event is now a preference.
 *          Added ability to return to home position after having moved to a preset position.
 *          Added logic to keep the app from sending duplicitous commands.
 *          Added movement locking mechanism to keep the camera from spazzing out when there is a lot of activity.
 *          Added ability to limit photos taken while in Home mode.
 *          Added a 4 second delay before taking a photo after movement has occurred to ensure that the camera has arrived at the requested
 *              location before the photo is taken. This may work more efficiently if a change in the switch6 attribute can trigger the
 *              photo burst...
 */
 
definition(
    name:        "D-Link Camera Event Manager",
    namespace:   "jmarkwell",
    author:      "Jordan Markwell",
    description: "For D-Link cameras using my device handlers. Move to preset positions, take photos, record video clips and send notifications.",
    category:    "Safety & Security",
    iconUrl:     "https://s3.amazonaws.com/smartapp-icons/Partner/photo-burst-when.png",
    iconX2Url:   "https://s3.amazonaws.com/smartapp-icons/Partner/photo-burst-when@2x.png",
    iconX3Url:   "https://s3.amazonaws.com/smartapp-icons/Partner/photo-burst-when@2x.png",
    pausable:    true
)

preferences {
    page(name: "mainPage")
    page(name: "eventPage")
    page(name: "cameraPage")
    page(name: "notificationPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", install: true, uninstall: true) {
        section() {
            paragraph title: "D-Link Camera Event Manager", "This app is compatible only with D-Link cameras using device handlers by BLebson or jmarkwell"
        }
        section("Main Configuration") {
            input "camera", "capability.imageCapture", title: "Camera", required: true
            
            href "eventPage", title: "Events", required: false
            href "cameraPage", title: "Camera Settings", required: true
        }
        section("Optional Settings") {
            input name: "debug", title: "Debug Logging", type: "bool", defaultValue: "false", required: false
            
            href "notificationPage", title: "Notification Settings", required: false
            
            label(title: "Assign a name", required: false)
            mode(title: "Set for specific mode(s)")
        }
    }
}

def eventPage() {
    dynamicPage(name: "eventPage", title: "Events") {
        section() {
            input "motion", "capability.motionSensor", title: "Motion Detected", required: false, multiple: true
            input "contact", "capability.contactSensor", title: "Contact Opens", required: false, multiple: true
            input "acceleration", "capability.accelerationSensor", title: "Acceleration Detected", required: false, multiple: true
            input "switchOn", "capability.switch", title: "Switch Turns On", required: false, multiple: true
            input "presenceArrival", "capability.presenceSensor", title: "Someone Arrives", required: false, multiple: true
            input "presenceDeparture", "capability.presenceSensor", title: "Someone Leaves", required: false, multiple: true
        }
    }
}

def cameraPage() {
    dynamicPage(name: "cameraPage") {
        section("Camera Settings") {
             input name: "recordVideo", title: "Record Video on Event", type: "bool", defaultValue: "false", required: false
             input name: "motionDuration", title: "Seconds of Inactivity after which to Stop Recording (motion event)", type: "number", defaultValue: 20, required: true
             paragraph "This value is also used to govern dwell time for a PTZ enabled camera."
             input name: "nonMotionDuration", title: "Duration of Video Clip (non-motion event)", type: "number", defaultValue: 60, required: true
             input name: "takePhoto", title: "Take Still Photo", type: "bool", defaultValue: "true", required: false
             input name: "burst", title: "Number of Photos to Take", type: "number", defaultValue: 3, required: true
             input name: "burstDelay", title: "Time Delay in Seconds Between Burst Photo Shots", type: "number", defaultValue: 3, required: true
             paragraph "Setting this value lower than 2 may result in errors."
             input name: "burstLimit", title: "In Home Mode, Allow Photo Burst Once Every X Minutes (0 for no limit)", type: "number", defaultValue: 5, required: false
             paragraph "This refers to Smart Home Monitor (Home) mode."
        }
        section("PTZ Options") {
            paragraph "The following options are for cameras with pan/tilt/zoom capabilities."
            input name: "moveEnabled", title: "Pan to Preset Position on Event", type: "bool", defaultValue: "false", required: false
            input name: "presetNum", title: "Preset Position Number", type: "number", defaultValue: 1 , required: true
            input name: "returnHome", title: "Return Home After Record Time Duration", type: "bool", defaultValue: "true", required: false
        }
    }
}

def notificationPage() {
    dynamicPage(name:"notificationPage", title:"Notification Settings") {
        section("Send this message in a push notification") {
            input "messageText", "text", title: "Message Text", required: false
        }
        section("Send message as text to this number") {
            input("recipients", "contact", title: "Send notifications to", required: false) {
                input "phone", "phone", title: "Phone Number", required: false
            }
        }
    }
}

def installed() {
    goHome()
    
    log.debug "Installed with settings: ${settings}"
    
    initialize()
}

def updated() {
    state.clear()
    
    goHome()
    
    log.debug "Updated with settings: ${settings}"
    
    unsubscribe()
    initialize()
}

def initialize() {
    subscribe(contact, "contact.open", eventHandler)
    subscribe(acceleration, "acceleration.active", eventHandler)
    subscribe(motion, "motion", eventHandler)
    subscribe(switchOn, "switch.on", eventHandler)
    subscribe(presenceArrival, "presence.present", eventHandler)
    subscribe(presenceDeparture, "presence.not present", eventHandler)
    subscribe(camera, "PTZPos", positionHandler)
}

def eventHandler(event) {
    def moveLockTime = camera.currentValue("moveLockTime") ?: 0
    
    def PTZPos = null
    switch ( camera.currentValue("PTZPos") ) {
        case "home":
            PTZPos = 1
            break
        case "presetOne":
            PTZPos = 1
            break
        case "presetTwo":
            PTZPos = 2
            break
        case "presetThree":
            PTZPos = 3
            break
    }
    
    if (debug) { log.debug "$event.name: $event.value" }
    
    // New events will modify the time frame of the following scheduled events.
    unschedule(goHome)
    unschedule(videoOff)
    
    if ( (event.name == "motion") && (event.value == "active") ) {
        if ( (recordVideo) && (camera.currentValue("switch4") == "off") ) {
            log.debug "Turning video recording on. (${event.name}: ${event.value})"
            camera.vrOn()
        }
        
        if (moveEnabled) {
            if ( (now() > moveLockTime) && (PTZPos != presetNum) ) {
                log.debug "Moving to preset ${presetNum}. (${event.name}: ${event.value})"
                camera.presetCommand(presetNum)
                
                if (debug) { log.debug "Setting ${motionDuration} second movement request lockout. (${event.name}: ${event.value})" }
                moveLockOn()
                
                moveDelayOn()
            }
            else if (PTZPos == presetNum) {
                // Enabling the following block will extend moveLock time periods when events (like a motion detection) take place.
                // if (now() <= moveLockTime) {
                    // if (debug) { log.debug "Setting ${motionDuration} second movement request lockout. (rescheduled) (${event.name}: ${event.value})" }
                // }
                // else {
                    // if (debug) { log.debug "Setting ${motionDuration} second movement request lockout. (${event.name}: ${event.value})" }
                // }
                // moveLockOn()
                
                snap()
            }
        }
        else { // if (!moveEnabled)
            snap()
        }
    }
    else if ( (event.name == "motion") && (event.value == "inactive") ) {
        if (recordVideo) {
            if (debug) { log.debug "Turning video recording off in ${motionDuration} seconds. (${event.name}: ${event.value})" }
            runIn(motionDuration, videoOff)
        }
        
        if ( (moveEnabled) && (returnHome) && (PTZPos != 1) ) {
            if (debug) { log.debug "Going home in ${motionDuration} seconds. (${event.name}: ${event.value})" }
            runIn(motionDuration, goHome)
        }
    }
    else if (event.name != "motion") {
        if ( (recordVideo) && (camera.currentValue("switch4") == "off") ) {
            log.debug "Turning video recording on. (${event.name}: ${event.value})"
            camera.vrOn()
            
            if (debug) { log.debug "Turning video recording off in ${nonMotionDuration} seconds. (${event.name}: ${event.value})" }
            runIn(nonMotionDuration, videoOff)
        }
        
        if (moveEnabled) {
            if ( (now() > moveLockTime) && (PTZPos != presetNum) ) {
                log.debug "Moving to preset ${presetNum}. (${event.name}: ${event.value})"
                camera.presetCommand(presetNum)
                
                if (debug) { log.debug "Setting ${nonMotionDuration} second movement request lockout. (${event.name}: ${event.value})" }
                moveLockOn()
                
                moveDelayOn()
                
                if ( (returnHome) && (presetNum != 1) ) { // Camera is not moving to home position.
                    if (debug) { log.debug "Going home in ${nonMotionDuration} seconds. (${event.name}: ${event.value})" }
                    runIn(nonMotionDuration, goHome)
                }
            }
            else if (PTZPos == presetNum) { // if camera is in preset position
                // Enabling the following block will extend moveLock time periods when events (like a motion detection) take place.
                // if (now() <= moveLockTime) {
                    // if (debug) { log.debug "Setting ${motionDuration} second movement request lockout. (rescheduled) (${event.name}: ${event.value})" }
                // }
                // else {
                    // if (debug) { log.debug "Setting ${motionDuration} second movement request lockout. (${event.name}: ${event.value})" }
                // }
                // moveLockOn()
                
                snap()
                
                if ( (returnHome) && (PTZPos != 1) ) { // Camera is not in home position.
                    if (debug) { log.debug "Going home in ${nonMotionDuration} seconds. (rescheduled) (${event.name}: ${event.value})" }
                    // goHome() unscheduled at start of function
                    runIn(nonMotionDuration, goHome)
                }
            }
        }
        else { // if (!moveEnabled)
            snap()
        }
    }
    
    if ( !( (event.name == "motion") && (event.value == "inactive") ) ) {
        sendNotification()
    }
}

def positionHandler(event) {
    // log.debug "positionHandler: [$event.name: $event.value]"
    
    if (debug) {
        switch ( camera.currentValue("PTZPos") ) {
            case "home":
                log.debug "The camera has moved to preset 1."
                break
            case "presetOne":
                log.debug "The camera has moved to preset 1."
                break
            case "presetTwo":
                log.debug "The camera has moved to preset 2."
                break
            case "presetThree":
                log.debug "The camera has moved to preset 3."
                break
            default:
                log.debug "The camera has moved to a non-preset position."
                break
        }
    }
    
    if (state.moveDelay) {
        state.moveDelay = false
        snap()
    }
}

def sendNotification() {
    if (messageText) {
        if (location.contactBookEnabled) {
            sendNotificationToContacts(messageText, recipients)
        }
        else {
            sendPush(messageText)
            if (phone) {
                sendSms(phone, messageText)
            }
        }
    }
}

def snap() {
    def PTZPos = null
    switch ( camera.currentValue("PTZPos") ) {
        case "home":
            PTZPos = 1
            break
        case "presetOne":
            PTZPos = 1
            break
        case "presetTwo":
            PTZPos = 2
            break
        case "presetThree":
            PTZPos = 3
            break
    }
    
    if ( (takePhoto) && (PTZPos == presetNum) && ( !state.photoLockTime || (now() > state.photoLockTime) ) ) {
        photoLockOn()
        
        def photosLeft = burst
        if (!recordVideo) { // Sending record and take commands at the same time can result in command failure.
            cameraTake()
            photosLeft = (burst - 1)
        }
        
        if (photosLeft > 0) {
            log.debug "Taking ${photosLeft} photo(s) with a ${burstDelay} second delay."
            for (int i in 1..photosLeft) {
                runIn( (burstDelay * i), cameraTake, [overwrite: false] )
            }
        }
    }
}

def cameraTake() {
    log.debug "Snapping a photo."
    camera.take()
}

def videoOff() {
    log.debug "Turning video recording off."
    camera.vrOff()
}

def goHome() {
    def PTZPos = camera.currentValue("PTZPos")
    if ( (PTZPos != "presetOne") && (PTZPos != "home") ) {
        log.debug "Moving to home position."
        camera.home()
    }
}

def moveDelayOn() {
    if (takePhoto) {
        if (debug) { log.debug "Photo will be taken when the camera reaches its destination preset." }
        state.moveDelay = true
    }
}

def moveLockOn() {
    def moveLockTime = camera.currentValue("moveLockTime") ?: 0
    if (now() > moveLockTime) {
        camera.moveLockOn(motionDuration)
    }
}

// photoLock is by design a lock local to each instance of D-Link Camera Event Manager.
// This allows events from other instances to trigger a photo if allowed by their own independent locks.
def photoLockOn() {
    if ( (location.mode == "Home") && (burstLimit) ) {
        if (debug) { log.debug "Setting ${burstLimit} minute photo request lockout. (location.mode: ${location.mode})" }
        state.photoLockTime = ( now() + (60000 * burstLimit) )
        if (debug) { log.debug "now(): ${now()} < photoLockTime: ${state.photoLockTime}" }
    }
    else if (location.mode != "Home") { // Block in case multiple events occur at the same time.
        def lockTime = (burst * burstDelay)
        if (debug) { log.debug "Setting ${lockTime} second photo request lockout. (location.mode: ${location.mode})" }
        state.photoLockTime = ( now() + (1000 * lockTime) )
        if (debug) { log.debug "now(): ${now()} < photoLockTime: ${state.photoLockTime}" }
    }
}
