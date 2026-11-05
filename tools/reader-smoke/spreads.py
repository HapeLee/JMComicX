"""Run after prepare.ps1/build/install. Uses the same isolated fixture as checks.py."""
import re,time,traceback
import checks as c

def rotate(value):
    c.adb('shell','settings','put','system','accelerometer_rotation','0')
    c.adb('shell','settings','put','system','user_rotation',str(value))
    time.sleep(1.5)

def launch(mode='LEFT_TO_RIGHT',page=2,double='AUTO',cover=True,wide=-1,chapter=1,seamless=True,background=True):
    c.adb('shell','am','force-stop','dev.jmx.client.debug');c.adb('logcat','-c')
    c.adb('shell','am','start','-W','-n','dev.jmx.client.debug/dev.jmx.client.ReaderModesSmokeActivity',
          '--es','mode',mode,'--es','double',double,'--ei','page',str(page),'--ei','chapter',str(chapter),
          '--ei','pages','6','--ei','wide',str(wide),'--ez','spreads','true','--ez','cover',str(cover).lower(),
          '--ez','seamless',str(seamless).lower(),'--ez','background',str(background).lower())
    time.sleep(1)

def visible_pages(nodes):
    images=[]
    for node in nodes:
        match=re.fullmatch(r'第 (\d+) 页',node.get('content-desc',''))
        if not match or node.get('off-screen'): continue
        bounds=[int(v) for v in re.findall(r'-?\d+',node['bounds'])]
        if bounds[0]>=0 and bounds[2]>bounds[0]: images.append((bounds[0],int(match[1])))
    return [page for _,page in sorted(images)]

def check(name,progress,pages):
    nodes=c.check(name,progress)
    actual=visible_pages(nodes)
    assert actual==pages,(name,pages,actual)
    return nodes

def seek(nodes,index,current=3):
    node=next(n for n in nodes if n.get('content-desc')=='阅读进度')
    left,top,right,bottom=[int(v) for v in re.findall(r'-?\d+',node['bounds'])]
    inset=(bottom-top)/2
    x=round(left+inset+(right-left-2*inset)*index/5)
    start=round(left+inset+(right-left-2*inset)*current/5)
    c.adb('shell','input','swipe',str(start),str((top+bottom)//2),str(x),str((top+bottom)//2),'700')

def suite():
    rotate(0);launch()
    check('auto-phone-single','smoke-1:2/6',[3])
    rotate(1)
    check('rotation-auto-pair-keeps-second-leaf','smoke-1:2/6',[2,3])
    c.adb('shell','input','keyevent','25')
    nodes=check('volume-advances-whole-spread','smoke-1:3/6',[4,5])
    seek(nodes,2)
    check('seek-second-leaf-keeps-exact-progress','smoke-1:2/6',[2,3])
    rotate(0)
    check('rotate-back-restores-second-leaf','smoke-1:2/6',[3])
    rotate(1);launch()
    c.adb('shell','input','keyevent','25')
    check('spread-forward','smoke-1:3/6',[4,5])
    c.adb('shell','input','keyevent','24')
    check('spread-backward-focuses-last-leaf','smoke-1:2/6',[2,3])
    launch(mode='RIGHT_TO_LEFT')
    check('rtl-physical-order','smoke-1:2/6',[3,2])
    c.adb('shell','input','keyevent','25')
    check('rtl-volume-next-spread','smoke-1:3/6',[5,4])
    launch(double='OFF')
    check('off-keeps-single-in-landscape','smoke-1:2/6',[3])
    launch(page=0,cover=False)
    check('cover-pairing-option','smoke-1:0/6',[1,2])
    launch(page=3,wide=3)
    check('wide-page-kept-single','smoke-1:3/6',[4])
    c.adb('shell','input','keyevent','25')
    check('portrait-pair-after-wide','smoke-1:4/6',[5,6])
    launch(page=5)
    check('odd-tail-single','smoke-1:5/6',[6])
    c.adb('shell','input','keyevent','25')
    check('continuous-next-cover-single','smoke-2:0/6',[1])
    c.adb('shell','input','keyevent','24')
    check('continuous-return-last-leaf','smoke-1:5/6',[6])
    zoom_checks()

def zoom_checks():
    rotate(0);launch(double='ALWAYS')
    check('forced-dual-on-phone','smoke-1:2/6',[2,3])
    c.adb('shell','am','start','-W','--activity-single-top','-n','dev.jmx.client.debug/dev.jmx.client.ReaderModesSmokeActivity','--es','gesture','pinch')
    time.sleep(.8)
    c.swipe('left')
    c.check('dual-pinch-pans-whole-spread','smoke-1:2/6')
    c.adb('shell','input tap 360 640; sleep 0.08; input tap 360 640')
    time.sleep(.6)
    c.swipe('left')
    check('dual-double-tap-restores-paging','smoke-1:3/6',[4,5])

if __name__=='__main__':
    error=None
    try: suite()
    except Exception:
        error=traceback.format_exc();raise
    finally:
        report='# Reader spreads smoke checks\n\n'+''.join('- PASS '+name+': '+value+'\n' for name,value in c.RESULTS)
        report+='\n'+('FAILED\n```\n'+error+'\n```\n' if error else 'All checks passed.\n')
        (c.ROOT/'spreads-report.md').write_text(report,encoding='utf-8')
