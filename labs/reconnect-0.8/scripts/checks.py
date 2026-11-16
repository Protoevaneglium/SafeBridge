from pathlib import Path
import re,xml.etree.ElementTree as ET
from urllib.parse import urlparse
root=Path(__file__).resolve().parents[1];a='{http://schemas.android.com/apk/res/android}'
m=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
s=(root/'app/src/main/java/com/stange/safebridge/MainActivity.java').read_text()
d=(root/'app/src/main/java/com/stange/safebridge/PageDeck.java').read_text()
p=(root/'app/src/main/java/com/stange/safebridge/PagedDialog.java').read_text()
code={x.name:x.read_text() for x in (root/'app/src/main/java').rglob('*.java')};allcode='\n'.join(code.values())
assert m.get('package')=='com.stange.safebridge.reconnectlab'
permissions={x.get(a+'name') for x in m.findall('uses-permission')}
assert permissions=={'android.permission.ACCESS_COARSE_LOCATION','android.permission.ACCESS_FINE_LOCATION','android.permission.FOREGROUND_SERVICE','android.permission.FOREGROUND_SERVICE_LOCATION','android.permission.POST_NOTIFICATIONS','android.permission.INTERNET'}
assert m.find('application').get(a+'allowBackup')=='false' and m.find('application').get(a+'usesCleartextTraffic')=='false'
services=m.find('application').findall('service');assert len(services)==2
assert all(x.get(a+'exported')=='false' and x.get(a+'foregroundServiceType')=='location' and x.get(a+'stopWithTask')=='false' for x in services)
for forbidden in ['ACTION_CALL','WebView','SharedPreferences','SEND_SMS','ScrollView','setTextScaleX','android.util.Log','System.out']:
    assert forbidden not in allcode,forbidden
for name,text in code.items():
    if name!='LiveSessionStore.java':assert 'FileOutputStream' not in text,name
    if name!='HttpsLiveTransport.java':assert 'URLConnection' not in text,name
assert 'public static final String ENDPOINT=""' in code['LiveConfig.java']
assert 'HttpsURLConnection' in code['HttpsLiveTransport.java'] and 'setInstanceFollowRedirects(false)' in code['HttpsLiveTransport.java']
for v in ['AndroidKeyStore','AES/GCM/NoPadding','AtomicFile','getNoBackupFilesDir','setRandomizedEncryptionRequired(true)']:
    assert v in code['LiveSessionStore.java'],v
print('PASS static lab: isolated package; optional permission allowlist; unexported location services; blank HTTPS endpoint; scoped network/encrypted-store code')
assert 'Intent.ACTION_DIAL' in s and 'Uri.parse("tel:911")' in s and 'You must then tap Call' in s
assert 'setNegativeButton("Cancel",null)' in s and 'finishAndRemoveTask' in s
assert s.count('catch(ActivityNotFoundException')==4
assert 'Intent.ACTION_SEND' in s and 'Intent.createChooser' in s
assert 'receipt are not confirmed' in s and 'Selecting an app or opening the sharing screen does not prove sending or receipt' in s
for v in ['Submitted reports cannot be canceled','every report available to law enforcement','not emergency response','may be monitored','increase danger','Do not download or forward','device where it was originally taken','mandatory reporters','Current session availability and schedule are unverified']:
    assert v in s,v
urls=re.findall(r'"(https://[^\"]+)"',s)
allowed={'humantraffickinghotline.org','report.cybertip.org','www.missingkids.org','takeitdown.ncmec.org','stopncii.org','www.techsafety.org','rainn.org','1in6.org','www.aftersilence.org'}
assert all(urlparse(x).hostname in allowed for x in urls)
print('PASS static lab: emergency remains dialer-only; explicit snapshot/read-link chooser; critical reporting/device/peer disclosures; reviewed provider domains')
assert 'choices("Support now","help","Find Me","findme")' in s
assert 'choices("Safety tools","tools","For someone","someone")' in s
assert 'control("Find Me",()->show("findme"))' in s
assert 'frame.addView(sessionStrip);updateSessionStrip();' in s
assert s.index('frame.addView(sessionStrip)') < s.index('frame.addView(deck')
assert 'status.pendingRevocation' in s and 'Retry revoke' in s
assert 'registerReceiver(stateReceiver' in s and 'LocalLocationService.ACTION_STATE' in s
assert 'deck.previousPage()' in s and 'navigation.restore(' in s and 'restoreReadingAnchor(anchor)' in s
assert 'proceed.setEnabled(last && !handled)' in p and 'view.isClickable())return false' in d
assert 'state.putIntArray("anchor",deck.getReadingAnchor())' in s
assert not any('location' in x.lower() or 'fix' in x.lower() or 'token' in x.lower() for x in re.findall(r'state.put\w+\("([^\"]+)"',s))
assert 'LiveConfig.configured()' in s and 'Live sharing is not set up in this test build' in s
for v in ['START_NOT_STICKY','FOREGROUND_SERVICE_TYPE_LOCATION','ACTION_STOP','ACTION_STATE']:
    assert v in code['LiveLocationService.java'],v
print('PASS static lab: direct support/family routes; pinned Find Me/active Stop; state broadcasts; preserved pagination/Back/rotation; honest unconfigured live gate')
assert 'Share until I stop' in s and 'Choose an end date & time' in s
assert 'LiveLocationService.EXTRA_END_AT_MS,liveEndAtMs' in s
assert 'new android.text.InputFilter.LengthFilter(1024)' in s
assert 'setOnCancel(()->liveAdmissionTicket="")' in s
print('PASS static reconnect lab: explicit Until Stop/chosen end and bounded transient access code')
print('Source assertions only. No Android runtime, device location, TalkBack, recipient delivery or screen-lock execution is implied.')
