/**
 *  Copyright 2015 SmartThings
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
 *  Wemo Service Manager
 *
 *  Author: superuser-ule (fix)
 *  Date: 2016-02-06
 *  Last Update : 2016-02-24
 */
definition(
    name: "Wemo Dimmer Advanced",
    namespace: "gundarev",
    author: "SmartThings,Ule",
    description: "Allows you to integrate your WeMo Dimmer  with SmartThings.",
    category: "SmartThings Labs",
    singleInstance: true,
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Partner/wemo.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Partner/wemo@2x.png"
    
    
)



preferences {
    page(name: "MainPage", title: "Search and config your Wemo Devices", install:true, uninstall: true){
    	section("") {
            href(name: "discover",title: "Discovery process",required: false,page: "wemoDiscovery",description: "tap to start searching")
            href(name: "watchdog",title: "Watchdog Settings",required: false,page: "wemoWatchdog", description: "tap to config the Watchdog Timer")
            input "interval", "number", title:"Set refresh minutes", defaultValue:5
            input "skipFirmware", "bool", title: "Skip firmware check", required: false, defaultValue: false
        }
    }
    page(name: "wemoWatchdog", title:"Config the Watchdog Timer")
    page(name: "wemoDiscovery", title:"Discovery Started!")
    
}
private discoverAllWemoTypes()
{
    log.trace "discoverAllWemoTypes"
	if(!state.subscribe) {
        subscribe(location, null, locationHandlerWemo, [filterEvents:false])
        state.subscribe = true
    }
    sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:Belkin:device:dimmer:1", physicalgraph.device.Protocol.LAN))
}

private getFriendlyName(String deviceNetworkId) {
	def hostAddress = getHostAddress(deviceNetworkId)
	log.trace "GET /setup.xml HTTP/1.1\r\nHOST: ${hostAddress} ${deviceNetworkId}"
	sendHubCommand(new physicalgraph.device.HubAction("""GET /setup.xml HTTP/1.1\r\nHOST: ${hostAddress}\r\n\r\n""", physicalgraph.device.Protocol.LAN, "${deviceNetworkId}"))
}

private getHostAddress(d) {
	def parts = d.split(":")
	def ip = convertHexToIP(parts[0])
	def port = convertHexToInt(parts[1])
	return ip + ":" + port
}
private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}
private verifyDevices() {
	def devices = getWemoDimmers().findAll { it?.value?.verified != true }
	devices.each {
		getFriendlyName((it.value.ip + ":" + it.value.port))
	}
}



def wemoDiscovery()
{
	if(canInstallLabs() || skipFirmware )
	{
		int refreshCount = !state.refreshCount ? 0 : state.refreshCount as int
		state.refreshCount = refreshCount + 1
		def refreshInterval = 5

		if(!state.subscribe) {
			subscribe(location, null, locationHandlerWemo, [filterEvents:false])
			state.subscribe = true
		}

		//ssdp request every 25 seconds
		if((refreshCount % 5) == 0) {
			discoverAllWemoTypes()
		}

		//setup.xml request every 5 seconds except on discoveries
		if(((refreshCount % 1) == 0) && ((refreshCount % 5) != 0)) {
			verifyDevices()
		}

		def dimmersDiscovered = dimmersDiscovered()

		return dynamicPage(name:"wemoDiscovery", title:"Discovery Started!", refreshInterval: refreshInterval) {
			section("Select a device...") {
				input "selectedDimmers", "enum", required:false, title:"Select Wemo Devices \n(${dimmersDiscovered.size() ?: 0} found)", multiple:true, options:dimmersDiscovered
			}
		}
	}
	else
	{
		def upgradeNeeded = """To use SmartThings Labs, your Hub should be completely up to date. To update your Hub, access Location Settings in the Main Menu (tap the gear next to your location name), select your Hub, and choose "Update Hub"."""

		return dynamicPage(name:"wemoDiscovery", title:"Upgrade needed!", nextPage:"", install:false, uninstall: true) {
			section("Upgrade") {
				paragraph "$upgradeNeeded"
			}
		}
	}
}

def wemoWatchdog(){
	dynamicPage(name: "wemoWatchdog") {
    	def anythingSet = anythingSet()
		if (anythingSet) {
			section("Verify Timer When"){
				ifSet "motion", "capability.motionSensor", title: "Motion Here", required: false, multiple: true
				ifSet "contact", "capability.contactSensor", title: "Contact Opens", required: false, multiple: true
				ifSet "contactClosed", "capability.contactSensor", title: "Contact Closes", required: false, multiple: true
				ifSet "acceleration", "capability.accelerationSensor", title: "Acceleration Detected", required: false, multiple: true
				ifSet "mySwitch", "capability.switch", title: "Switch Turned On", required: false, multiple: true
				ifSet "mySwitchOff", "capability.switch", title: "Switch Turned Off", required: false, multiple: true
				ifSet "arrivalPresence", "capability.presenceSensor", title: "Arrival Of", required: false, multiple: true
				ifSet "departurePresence", "capability.presenceSensor", title: "Departure Of", required: false, multiple: true
				ifSet "smoke", "capability.smokeDetector", title: "Smoke Detected", required: false, multiple: true
				ifSet "water", "capability.waterSensor", title: "Water Sensor Wet", required: false, multiple: true
                ifSet "temperature", "capability.temperatureMeasurement", title: "Temperature", required: false, multiple: true
                ifSet "powerMeter", "capability.powerMeter", title: "Power Meter", required: false, multiple: true
                ifSet "energyMeter", "capability.energyMeter", title: "Energy", required: false, multiple: true
                ifSet "signalStrength", "capability.signalStrength", title: "Signal Strength", required: false, multiple: true
				ifSet "button1", "capability.button", title: "Button Press", required:false, multiple:true //remove from production
				ifSet "triggerModes", "mode", title: "System Changes Mode", required: false, multiple: true
			}
		}
		def hideable = anythingSet || (app.installationState == "COMPLETE" && anythingSet)
		def sectionTitle = anythingSet ? "Select additional triggers" : "Verify Timer When..."

		section(sectionTitle, hideable: hideable, hidden: true){
			ifUnset "motion", "capability.motionSensor", title: "Motion Here", required: false, multiple: true
			ifUnset "contact", "capability.contactSensor", title: "Contact Opens", required: false, multiple: true
			ifUnset "contactClosed", "capability.contactSensor", title: "Contact Closes", required: false, multiple: true
			ifUnset "acceleration", "capability.accelerationSensor", title: "Acceleration Detected", required: false, multiple: true
			ifUnset "mySwitch", "capability.switch", title: "Switch Turned On", required: false, multiple: true
			ifUnset "mySwitchOff", "capability.switch", title: "Switch Turned Off", required: false, multiple: true
			ifUnset "arrivalPresence", "capability.presenceSensor", title: "Arrival Of", required: false, multiple: true
			ifUnset "departurePresence", "capability.presenceSensor", title: "Departure Of", required: false, multiple: true
			ifUnset "smoke", "capability.smokeDetector", title: "Smoke Detected", required: false, multiple: true
			ifUnset "water", "capability.waterSensor", title: "Water Sensor Wet", required: false, multiple: true
			ifUnset "temperature", "capability.temperatureMeasurement", title: "Temperature", required: false, multiple: true
            ifUnset "signalStrength", "capability.signalStrength", title: "Signal Strength", required: false, multiple: true
            ifUnset "powerMeter", "capability.powerMeter", title: "Power Meter", required: false, multiple: true
            ifUnset "energyMeter", "capability.energyMeter", title: "Energy Meter", required: false, multiple: true
			ifUnset "button1", "capability.button", title: "Button Press", required:false, multiple:true //remove from production
			ifUnset "holdablebutton1", "capability.holdablebutton", title: "Holdable Button Press", required:false, multiple:true //remove from production
			ifUnset "triggerModes", "mode", title: "System Changes Mode", description: "Select mode(s)", required: false, multiple: true
		}
    }
}

private anythingSet() {
	for (name in ["motion","contact","contactClosed","acceleration","mySwitch","mySwitchOff","arrivalPresence","departurePresence","smoke","water", "temperature","signalStrength","powerMeter","energyMeter","button1","holdablebutton1","timeOfDay","triggerModes","timeOfDay"]) {
		if (settings[name]) {
			return true
		}
	}
	return false
}

private ifUnset(Map options, String name, String capability) {
	if (!settings[name]) {
		input(options, name, capability)
	}
}

private ifSet(Map options, String name, String capability) {
	if (settings[name]) {
		input(options, name, capability)
	}
}

def dimmersDiscovered() {
	def dimmers = getWemoDimmers().findAll { it?.value?.verified == true }
	def map = [:]
	dimmers.each {
		def value = it.value.name ?: "WeMo Dimmer ${it.value.ssdpUSN.split(':')[1][-3..-1]}"
		def key = it.value.mac
		map["${key}"] = value
	}
	map
}

def getWemoDimmers()
{
	if (!state.switches) { state.switches = [:] }
	state.switches
}


def installed() {
}

def updated() {
    unsubscribe()
	state.subscribe = false
	if (selectedDimmers)
	{
		addDimmers()
	}
    subscribeToEvents()
    scheduleActions()
	scheduledActionsHandler()
}



def subscribeToEvents() {
	//subscribe(app, appTouchHandler)
	subscribe(contact, "contact.open", eventHandler)
	subscribe(contactClosed, "contact.closed", eventHandler)
	subscribe(acceleration, "acceleration.active", eventHandler)
	subscribe(motion, "motion.active", eventHandler)
	subscribe(mySwitch, "switch.on", eventHandler)
	subscribe(mySwitchOff, "switch.off", eventHandler)
	subscribe(arrivalPresence, "presence.present", eventHandler)
	subscribe(departurePresence, "presence.not present", eventHandler)
	subscribe(smoke, "smoke.detected", eventHandler)
	subscribe(smoke, "smoke.tested", eventHandler)
	subscribe(smoke, "carbonMonoxide.detected", eventHandler)
	subscribe(water, "water.wet", eventHandler)
    subscribe(temperature, "temperature", eventHandler)
    subscribe(powerMeter, "power", eventHandler)
	subscribe(energyMeter, "energy", eventHandler)
    subscribe(signalStrength, "lqi", eventHandler)
    subscribe(signalStrength, "rssi", eventHandler)
	subscribe(button1, "button.pushed", eventHandler)
    subscribe(holdablebutton1, "holdableButton.pushed", eventHandler)
     subscribe(holdablebutton1, "holdableButton.held", eventHandler)
 
	if (triggerModes) {
		subscribe(location, modeChangeHandler)
	}
}
def eventHandler(evt) {
    takeAction(evt)
}
def modeChangeHandler(evt) {
	if (evt.value in triggerModes) {
		eventHandler(evt)
	}
}



private scheduleActions() {
    def minutes = Math.max(settings.interval.toInteger(),1)
    def cron = "0 0/${minutes} * * * ?"
   	schedule(cron, scheduledActionsHandler)
}
def scheduledActionsHandler() {
    state.actionTime = new Date().time
    refreshDevices()
    discoverAllWemoTypes()
}

def resubscribe() {
	refresh()
}
def refreshDevices() {
	def devices = getAllChildDevices()
	devices.each { d ->
		log.debug "Calling refresh() on device: ${d.id}"
		d.refresh()
	}
}
def subscribeToDevices() {
	def devices = getAllChildDevices()
	devices.each { d ->
		d.subscribe()
	}
}

def addDimmers() {
	def dimmers = getWemoDimmers()

	selectedDimmers.each { dni ->
		def selectedDimmer = dimmers.find { it.value.mac == dni } ?: dimmers.find { "${it.value.ip}:${it.value.port}" == dni }
		def d
		if (selectedDimmer) {
			d = getChildDevices()?.find {
				it.dni == selectedDimmer.value.mac || it.device.getDataValue("mac") == selectedDimmer.value.mac
			}
		}

		if (!d) {
			log.debug "Creating WeMo with dni: ${selectedDimmer.value.mac}"
			def name
			def namespace = "gundarev"
			switch (selectedDimmer.value.ssdpTerm){
				case ~/.*dimmer.*/: 
					name = "Wemo Dimmer"
					break
				
				
				
			}
 			d = addChildDevice(namespace, name , selectedDimmer.value.mac, selectedDimmer?.value.hub, [ 
            		"label":  selectedDimmer?.value?.name ?:"Wemo Device",
					"data": [
							"mac": selectedDimmer.value.mac,
							"ip": selectedDimmer.value.ip,
							"port": selectedDimmer.value.port
					]
			])
			log.debug "Created ${d.displayName} with id: ${d.id}, dni: ${d.deviceNetworkId}"
		} else {
			log.debug "found ${d.displayName} with id $dni already exists"
		}
	}
}

def initialize() {
	// remove location subscription afterwards
	 unsubscribe()
	 state.subscribe = false

	if (selectedDimmers)
	{
		addDimmers()
	}
}

def locationHandlerWemo(evt) {
	def description = evt.description
	def hub = evt?.hubId
	def parsedEvent = parseDiscoveryMessage(description)
	parsedEvent << ["hub":hub]
    def msg = parseLanMessage(description)

	if (parsedEvent?.ssdpTerm?.contains("Belkin:device:dimmer") ) {

		def dimmers = getWemoDimmers()

		if (!(dimmers."${parsedEvent.ssdpUSN.toString()}"))
		{ //if it doesn't already exist
			dimmers << ["${parsedEvent.ssdpUSN.toString()}":parsedEvent]
		}
		else
		{ // just update the values

			def d = dimmers."${parsedEvent.ssdpUSN.toString()}"
			boolean deviceChangedValues = false

			if(d.ip != parsedEvent.ip || d.port != parsedEvent.port) {
				d.ip = parsedEvent.ip
				d.port = parsedEvent.port
				deviceChangedValues = true
			}

			if (deviceChangedValues) {
				def children = getChildDevices()
				children.each {
					if (it.getDeviceDataByName("mac") == parsedEvent.mac) {
						log.debug "updating ip and port, and resubscribing, for device ${it} with mac ${parsedEvent.mac}"
						it.subscribe(parsedEvent.ip, parsedEvent.port)
					}
				}
			}

		}

	}
	else if (parsedEvent.headers && parsedEvent.body) {
		String headerString = new String(parsedEvent.headers.decodeBase64())?.toLowerCase()
		if (headerString != null && (headerString.contains('text/xml') || headerString.contains('application/xml'))) {
			def body = parseXmlBody(parsedEvent.body)
			if ( body?.device?.deviceType?.text().startsWith("urn:Belkin:device:dimmer"))
			{
				def dimmers = getWemoDimmers()
				def wemoDimmer = dimmers.find {it?.key?.contains(body?.device?.UDN?.text())}
				if (wemoDimmer)
				{
					wemoDimmer.value << [name:body?.device?.friendlyName?.text(), verified: true]
				}
				else
				{
					log.error "/setup.xml returned a wemo device that didn't exist"
				}
			}
		}
	}
}

def appTouchHandler(evt) {
	takeAction(evt)
}

private takeAction(evt) {
	def eventTime = new Date().time
	if (eventTime > ( 60000 + Math.max((settings.interval ? settings.interval.toInteger():0),3) * 1000 * 60 + (state.actionTime?:0))) {
		scheduledActionsHandler()
	}
}
private def parseXmlBody(def body) {
	def decodedBytes = body.decodeBase64()
	def bodyString
	try {
		bodyString = new String(decodedBytes)
	} catch (Exception e) {
		// Keep this log for debugging StringIndexOutOfBoundsException issue
		throw e
	}
	return new XmlSlurper().parseText(bodyString)
}

private def parseDiscoveryMessage(String description) {
	def device = [:]
	def parts = description.split(',')
	parts.each { part ->
		part = part.trim()
		if (part.startsWith('devicetype:')) {
			def valueString = part.split(":")[1].trim()
			device.devicetype = valueString
		}
		else if (part.startsWith('mac:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				device.mac = valueString
			}
		}
		else if (part.startsWith('networkAddress:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				device.ip = valueString
			}
		}
		else if (part.startsWith('deviceAddress:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				device.port = valueString
			}
		}
		else if (part.startsWith('ssdpPath:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				device.ssdpPath = valueString
			}
		}
		else if (part.startsWith('ssdpUSN:')) {
			part -= "ssdpUSN:"
			def valueString = part.trim()
			if (valueString) {
				device.ssdpUSN = valueString
			}
		}
		else if (part.startsWith('ssdpTerm:')) {
			part -= "ssdpTerm:"
			def valueString = part.trim()
			if (valueString) {
				device.ssdpTerm = valueString
			}
		}
		else if (part.startsWith('headers')) {
			part -= "headers:"
			def valueString = part.trim()
			if (valueString) {
				device.headers = valueString
			}
		}
		else if (part.startsWith('body')) {
			part -= "body:"
			def valueString = part.trim()
			if (valueString) {
				device.body = valueString
			}
		}
	}

	device
}



def pollChildren() {
	def devices = getAllChildDevices()
	devices.each { d ->
		d.poll()
	}
}

def delayPoll() {
	runIn(5, "pollChildren")
}

private Boolean canInstallLabs()
{
	return hasAllHubsOver("000.011.00603")
}

private Boolean hasAllHubsOver(String desiredFirmware)
{
	return realHubFirmwareVersions.every { fw -> fw >= desiredFirmware }
}

private List getRealHubFirmwareVersions()
{
	return location.hubs*.firmwareVersionString.findAll { it }
}