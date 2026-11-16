from pathlib import Path
from urllib.parse import urlparse
import re, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
a='{http://schemas.android.com/apk/res/android}'
m=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
s=(root/'app/src/main/java/com/stange/safebridge/MainActivity.java').read_text()
d=(root/'app/src/main/java/com/stange/safebridge/PageDeck.java').read_text()
p=(root/'app/src/main/java/com/stange/safebridge/PagedDialog.java').read_text()
all_code='\n'.join(x.read_text() for x in (root/'app/src/main/java').rglob('*.java'))
assert m.get('package')=='com.stange.safebridge.pasteltest'
permissions={x.get(a+'name') for x in m.findall('uses-permission')}
assert permissions=={'android.permission.ACCESS_COARSE_LOCATION','android.permission.ACCESS_FINE_LOCATION','android.permission.FOREGROUND_SERVICE','android.permission.FOREGROUND_SERVICE_LOCATION','android.permission.POST_NOTIFICATIONS'}
service=m.find('application/service')
assert service.get(a+'exported')=='false' and service.get(a+'foregroundServiceType')=='location'
assert service.get(a+'stopWithTask')=='false' 
assert m.find('application').get(a+'allowBackup')=='false'
assert m.find('application').get(a+'usesCleartextTraffic')=='false'
activity=m.find('application/activity')
assert activity.get(a+'excludeFromRecents')=='true'
assert activity.get(a+'enableOnBackInvokedCallback')=='false'
for x in ['ACTION_CALL','WebView','SharedPreferences','FileOutputStream','URLConnection','HttpClient','SEND_SMS','ScrollView','setTextScaleX']:
    assert x not in all_code,x
assert s.count('startActivity(')==3
assert 'Intent.ACTION_SEND' in s and 'Intent.createChooser' in s
assert 'Sending and receipt are not confirmed' in s
assert 'Intent.ACTION_DIAL' in s and 'Uri.parse("tel:911")' in s
assert 'You must then tap Call' in s
assert s.count('catch(ActivityNotFoundException')==3
assert 'setNegativeButton("Cancel",null)' in s
assert 'setOnLongClickListener' in s and 'Switch to calculator' in s
assert 'COMPONENT_ENABLED_STATE_DISABLED' in s and 'DONT_KILL_APP' in s
assert 'if(section.equals("calculator")){finishAndRemoveTask();return;}' in s
aliases=m.find('application').findall('activity-alias')
assert len(aliases)==2 and {x.get(a+'enabled') for x in aliases}=={'true','false'}
assert {x.get(a+'label') for x in aliases}=={'Safe Bridge UI Test','Calculator'}
print('PASS static: optional location permissions; no app INTERNET/background-location permission; dialer-only confirmed handoffs; missing-handler branches; calculator aliases/exit')
for x in ['Submitted reports cannot be canceled','every report available to law enforcement','Your name and contact details are optional','No one receives messages here','not emergency response','may be monitored','does not erase traces','increase danger','Do not download or forward','device where it was originally taken','not fully confidential','18+','mandatory reporters','Chat may be unavailable','no partnerships']:
    assert x in s,x
assert 'disclosure=CYBERTIP_POLICY' in s and 'disclosure=HOTLINE_POLICY' in s
assert 'if(url.startsWith("https://takeitdown.ncmec.org/"))disclosure=' in s
urls=re.findall(r'"(https://[^\"]+)"',s)
allowed={'humantraffickinghotline.org','report.cybertip.org','www.missingkids.org','takeitdown.ncmec.org','stopncii.org','www.techsafety.org','rainn.org','1in6.org','www.aftersilence.org'}
assert urls and all(urlparse(x).hostname in allowed for x in urls)
assert 'aftersilence.org/join.php' not in s
print('PASS static: critical safety/reporting/peer disclosures; reviewed domain allowlist; current forum eligibility link')
sections=set(re.findall(r'case "([^"]+)":',s))|{'about'}
targets=set(re.findall(r'route\("[^"\n]+","([^"]+)"\)',s))
for match in re.findall(r'choices\("[^"]+","([^"]+)","[^"]+","([^"]+)"\)',s):targets.update(match)
assert targets<=sections,targets-sections
assert {'help','signs','tools','peers','child','images','findme','quiet','staying','distress','someone','resources'} <= targets
home_start=s.index('case "home":')
home=s[home_start:s.index('case "urgent":',home_start)]
assert home.count('choices(')==4 and 'resource(' not in home
assert s.index('link("Call trafficking hotline') < s.index('detail("Privacy before contacting')
assert 'deck.previousPage()' in s and 'navigation.back(deck.getPageIndex())' in s
assert 'navigation.restore(' in s and 'state.putStringArray("routes"' in s
assert 'PagedDialog.dismissFor(this)' in s
assert 'proceed.setEnabled(false)' in p and 'proceed.setEnabled(last && !handled)' in p
assert 'if(handled' in p and 'isShowing(Activity' in p
assert 'view.isClickable())return false' in d
assert 'Expand window' in d and 'setAccessibilityHeading' in d
assert 'setMinHeight(dp(48))' in d+p+s
print('PASS static: reachable route graph; compact home choices; Back/Bundle contracts; single dialog; final-page gate; whole action labels; minimum touch targets')
print('These are source assertions, not Android runtime, layout, accessibility or emergency-service tests.')

loc=(root/'app/src/main/java/com/stange/safebridge/LocationSnapshot.java').read_text()
fg=(root/'app/src/main/java/com/stange/safebridge/LocalLocationService.java').read_text()
assert 'getCurrentLocation' in loc and 'TIMEOUT_MILLIS=20_000L' in loc
assert 'void cancel()' in loc and 'void lastKnown()' in loc
assert 'START_NOT_STICKY' in fg and 'FOREGROUND_SERVICE_TYPE_LOCATION' in fg
assert 'stopForeground(STOP_FOREGROUND_REMOVE)' in fg and 'locations.removeUpdates(this)' in fg
assert 'AlarmManager.ELAPSED_REALTIME_WAKEUP' in fg and 'SystemClock.elapsedRealtime()>=deadline' in fg
assert 'No live link is available in this build' in s
assert 'Current session availability and schedule are unverified' in s
assert 'putIntArray("anchor",deck.getReadingAnchor())' in s
assert not any('location' in x.lower() or 'fix' in x.lower() for x in re.findall(r'state.put\w+\("([^"]+)"',s))
print('PASS static: optional one-shot/local-only location; no auto-sending; expiry/stop contracts; no coordinates in saved activity state; live-backend limitation')

features={x.get(a+'name'):x.get(a+'required') for x in m.findall('uses-feature')}
assert all(features.get(x)=='false' for x in ['android.hardware.location','android.hardware.location.gps','android.hardware.location.network'])
print('PASS static: location hardware optional; offline guidance remains available without location capability')
