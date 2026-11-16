from pathlib import Path
import re, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
m=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
s=(root/'app/src/main/java/com/stange/safebridge/MainActivity.java').read_text()
a='{http://schemas.android.com/apk/res/android}'
assert not m.findall('uses-permission')
assert m.find('application').get(a+'allowBackup')=='false'
assert m.find('application').get(a+'usesCleartextTraffic')=='false'
assert m.find('application/activity').get(a+'excludeFromRecents')=='true'
for forbidden in ['ACTION_CALL','WebView','SharedPreferences','FileOutputStream','URLConnection','HttpClient','ACCESS_FINE_LOCATION','SEND_SMS']:
    assert forbidden not in s,forbidden
for required in ['Intent.ACTION_DIAL','setNegativeButton("Cancel"','ActivityNotFoundException','finishAndRemoveTask','No one receives messages here','does not erase traces','Do not download or forward','Not for public deployment yet','participating public or unencrypted','device where the image was originally taken','all websites or encrypted messaging','Uninstalling may still leave','This does not describe the privacy practices','Your name and identifying details are optional','Opening this page does not submit a report','disclosure=CYBERTIP_POLICY','disclosure=HOTLINE_POLICY','https://humantraffickinghotline.org/en/confidentiality-policy']:
    assert required in s,required
for route in ['home','help','child','images','safety','about']:
    assert '"'+route+'"' in s
urls=re.findall(r'"(https://[^\"]+)"',s)
allowed={'humantraffickinghotline.org','report.cybertip.org','www.missingkids.org','takeitdown.ncmec.org','stopncii.org','www.techsafety.org'}
from urllib.parse import urlparse
assert urls and all(urlparse(x).hostname in allowed for x in urls)
print('PASS: manifest/privacy boundaries, intent confirmation, route coverage, resource allowlist, safety text (static only)')

# Emergency handoff and disguise controls are static checks, not Android execution.
assert 'Uri.parse("tel:911")' in s
assert 'You must then tap Call' in s
assert 'setOnLongClickListener' in s and 'Switch to calculator' in s
assert 'COMPONENT_ENABLED_STATE_DISABLED' in s and 'DONT_KILL_APP' in s
assert 'if(section.equals("calculator")){finishAndRemoveTask();return;}' in s
aliases=m.find('application').findall('activity-alias')
assert len(aliases)==2
assert {x.get(a+'enabled') for x in aliases}=={'true','false'}
assert {x.get(a+'label') for x in aliases}=={'Safe Bridge UI Test','Calculator'}
assert all(x.get(a+'targetActivity')=='com.stange.safebridge.MainActivity' for x in aliases)
print('PASS: emergency-only dialer, persistent launcher aliases, calculator exit and explicit disguise limits (static only)')

assert s.count('setNeutralButton("Calculator"')==4
print("PASS: calculator escape available in all external/emergency confirmation and missing-handler dialogs (static only)")

# UI structure only; not a TalkBack or Android rendering test.
assert 'info.setClassName(Button.class.getName())' in s
assert 'IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS' in s
assert 'android.R.attr.state_pressed' in s and 'android.R.attr.state_focused' in s
assert 'interactive(b,box(colors[r])' in s and 'interactive(panic,' in s
print('PASS: resource-card button semantics and control pressed/focus states (static only)')

# Page and sheet contracts are source checks, not an Android layout test.
deck=(root/'app/src/main/java/com/stange/safebridge/PageDeck.java').read_text()
sheet=(root/'app/src/main/java/com/stange/safebridge/PagedDialog.java').read_text()
assert 'ScrollView' not in s+deck+sheet
assert 'proceed.setEnabled(false)' in sheet and 'proceed.setEnabled(last)' in sheet
assert 'remaining=remaining.substring(best)' in deck
assert "remaining.lastIndexOf(' ',best-1)" in deck
assert 'calculatorPage();int index=frame.indexOfChild(deck)' in s
print('PASS: no scrolling widgets, final disclosure-page gate, text continuation, dedicated calculator (static only)')
assert 'position.setText("Expand window")' in deck
