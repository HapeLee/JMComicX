import subprocess,json,time,pathlib,os,traceback
import sys
sys.stdout.reconfigure(encoding="utf-8")
ROOT=pathlib.Path(os.environ.get("JMX_SMOKE_OUTPUT",str(pathlib.Path(__file__).resolve().parents[2]/"app/build/reader-smoke")))
ROOT.mkdir(parents=True,exist_ok=True)
SERIAL=os.environ.get("JMX_SMOKE_DEVICE","emulator-5554")
ADB=["adb","-s",SERIAL]
RESULTS=[]
def run(*args):
 r=subprocess.run(list(args),capture_output=True)
 if r.returncode: raise RuntimeError(r.stderr.decode('utf-8','replace'))
 return r.stdout.decode('utf-8','replace')
def adb(*args): return run(*ADB,*args)
def layout(name):
 p=ROOT/(name+'.json');run('android','layout','--device',SERIAL,'--flat','--full','-o',str(p))
 return json.loads(p.read_text(encoding='utf-8'))
def progress():
 for attempt in range(32):
  time.sleep(.25)
  pid=adb('shell','pidof','io.kazusa.jmcomic.debug').strip()
  if not pid: raise AssertionError('Reader app is not running')
  log=adb('logcat','-d','--pid='+pid.split()[0],'-s','ReaderModeSmoke:I','AndroidRuntime:E')
  if 'FATAL EXCEPTION' in log: raise AssertionError(log[-8000:])
  lines=[line.split('ReaderModeSmoke: ')[-1] for line in log.splitlines() if 'ReaderModeSmoke:' in line]
  if lines: return lines[-1]
 raise AssertionError('No progress was emitted after the activity finished launching')
def check(name,expected):
 d=layout(name);actual=progress()
 if actual!=expected: raise AssertionError(f'{name}: expected {expected}, got {actual}')
 RESULTS.append((name,actual))
 print('PASS',name,actual,flush=True)
 return d
def launch(mode='LEFT_TO_RIGHT',chapter=1,page=1,seamless=True,missing=-1):
 adb('shell','am','force-stop','io.kazusa.jmcomic.debug');adb('logcat','-c')
 adb('shell','am','start','-W','-n','io.kazusa.jmcomic.debug/io.kazusa.jmcomic.ReaderModesSmokeActivity','--es','mode',mode,'--ei','chapter',str(chapter),'--ei','page',str(page),'--ez','seamless',str(seamless).lower(),'--ei','missing',str(missing))
 time.sleep(1)
def swipe(direction):
 coords={'left':(580,640,140,640),'right':(140,640,580,640),'up':(360,1000,360,300),'down':(360,300,360,1000)}[direction]
 adb('shell','input','swipe',*(str(x) for x in coords),'500')

def tap_node(node):
 import re
 coords=[int(x) for x in re.findall(r"-?\d+",node['center'])]
 adb('shell','input','tap',*(str(x) for x in coords))
def select_mode(label):
 adb('shell','input','tap','640','112')
 d=layout('mode-settings-open')
 tap_node(next(n for n in d if n.get('text')=='\u9605\u8bfb\u6a21\u5f0f'))
 d=layout('mode-options-open')
 tap_node(next(n for n in d if n.get('text')==label))
 d=layout('mode-settings-changed')
 tap_node(next(n for n in d if n.get('content-desc')=='\u5173\u95ed\u9605\u8bfb\u8bbe\u7f6e'))

def scroll_until(name,direction,expected_prefix,original_prefix):
 # A free-scrolling strip has no fixed swipe-to-page ratio; advance until the next chapter is actually visible.
 for step in range(6):
  swipe(direction)
  d=layout(name+'-'+str(step));actual=progress()
  if actual.startswith(expected_prefix):
   RESULTS.append((name,actual));print('PASS',name,actual,flush=True)
   return d
  if not actual.startswith(original_prefix): raise AssertionError((name,actual))
 raise AssertionError(name+': chapter boundary was not crossed')

def zoom_checks():
 launch('LEFT_TO_RIGHT')
 check('zoom-start','smoke-1:1/3')
 adb('shell','am','start','-W','--activity-single-top','-n','io.kazusa.jmcomic.debug/io.kazusa.jmcomic.ReaderModesSmokeActivity','--es','gesture','pinch')
 time.sleep(.8)
 swipe('left');check('pinch-pan-keeps-page','smoke-1:1/3')
 adb('shell','input tap 360 640; sleep 0.08; input tap 360 640')
 time.sleep(.6)
 swipe('left');check('double-tap-restores-paging','smoke-1:2/3')

def suite():
 for mode,direction,back,slug in [
  ('LEFT_TO_RIGHT','left','right','ltr'),
  ('RIGHT_TO_LEFT','right','left','rtl'),
  ('TOP_TO_BOTTOM','up','down','vertical')]:
  launch(mode)
  check(slug+'-start','smoke-1:1/3')
  swipe(direction);check(slug+'-last','smoke-1:2/3')
  swipe(direction);check(slug+'-cross','smoke-2:0/3')
  swipe(back);check(slug+'-return','smoke-1:2/3')
  swipe(direction);check(slug+'-cross-again','smoke-2:0/3')
 launch('WEBTOON')
 check('webtoon-start','smoke-1:1/3')
 adb('shell','input','swipe','360','900','360','600','800');d=check('webtoon-long-page','smoke-1:1/3')
 before=next(n['bounds'] for n in d if n.get('content-desc')=='\u7b2c 2 \u9875')
 select_mode('\u4ece\u5de6\u5230\u53f3');check('switch-to-pager','smoke-1:1/3')
 select_mode('\u6761\u6f2b');d=check('switch-back-to-strip','smoke-1:1/3')
 after=next(n['bounds'] for n in d if n.get('content-desc')=='\u7b2c 2 \u9875')
 import re
 assert all(abs(int(a)-int(b))<=3 for a,b in zip(re.findall(r'-?\d+',before),re.findall(r'-?\d+',after))),(before,after)
 scroll_until('webtoon-cross','up','smoke-2:','smoke-1:')
 scroll_until('webtoon-return','down','smoke-1:','smoke-2:')
 launch('LEFT_TO_RIGHT')
 check('volume-start','smoke-1:1/3')
 adb('shell','input','keyevent','25');check('volume-next','smoke-1:2/3')
 adb('shell','input','keyevent','25');check('volume-cross','smoke-2:0/3')
 adb('shell','input','keyevent','24');check('volume-return','smoke-1:2/3')
 zoom_checks()
 launch('LEFT_TO_RIGHT',page=2,seamless=False)
 check('single-chapter-start','smoke-1:2/3')
 swipe('left');check('single-chapter-stops','smoke-1:2/3')
 launch('LEFT_TO_RIGHT',page=2,missing=2)
 check('missing-start','smoke-1:2/3')
 swipe('left');d=check('missing-stops','smoke-1:2/3')
 assert any(n.get('text')=='\u91cd\u8bd5' for n in d)
 tap_node(next(n for n in d if n.get('text')=='\u91cd\u8bd5'))
 d=check('missing-retry-stays','smoke-1:2/3')
 tap_node(next(n for n in d if n.get('text')=='\u8df3\u81f3\u5df2\u4e0b\u8f7d\u7ae0\u8282'))
 check('missing-explicit-skip','smoke-3:0/3')
 launch('LEFT_TO_RIGHT',chapter=4,page=2)
 check('book-end-start','smoke-4:2/3')
 swipe('left');d=check('book-end-no-wrap','smoke-4:2/3')
 assert any(n.get('text')=='\u5df2\u8bfb\u5b8c\u5168\u90e8\u7ae0\u8282' for n in d)
 swipe('right');check('book-end-return','smoke-4:2/3')

if __name__=='__main__':
 error=None
 try: suite()
 except Exception:
  error=traceback.format_exc()
  raise
 finally:
  report='# Reader mode smoke checks\n\n'+''.join('- PASS '+name+': '+progress+'\n' for name,progress in RESULTS)
  report+='\n'+('FAILED\n```\n'+error+'\n```\n' if error else 'All checks passed.\n')
  (ROOT/'report.md').write_text(report,encoding='utf-8')
