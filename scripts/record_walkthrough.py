"""Record the real hosted demo with readable typing, scrolling and pointer motion.

Requires Playwright and Chromium. Start the hosted profile locally on port 8787,
then run this script. The recording uses only its own fictional visitor data.
"""
import asyncio
import math
import random
from pathlib import Path
from playwright.async_api import async_playwright

OUT = Path('dist/walkthrough')
POINTER = r"""(() => {
  addEventListener('DOMContentLoaded', () => {
    const p = document.createElement('div');
    p.id = 'walkthrough-pointer';
    p.style.cssText = 'position:fixed;z-index:2147483647;pointer-events:none;width:26px;height:30px;filter:drop-shadow(0 1px 2px #0006)';
    p.innerHTML = '<svg width="26" height="30" viewBox="0 0 26 30"><path d="M3 2 L3 24 L9 18 L14 28 L18 26 L13 16 L23 16 Z" fill="#fff" stroke="#153c31" stroke-width="2"/></svg>';
    const xy = JSON.parse(sessionStorage.getItem('pointer') || '[1150,750]');
    p.style.left=xy[0]+'px'; p.style.top=xy[1]+'px';
    document.body.appendChild(p);
    addEventListener('mousemove', e => {p.style.left=e.clientX+'px';p.style.top=e.clientY+'px';sessionStorage.setItem('pointer',JSON.stringify([e.clientX,e.clientY]));});
    addEventListener('mousedown', () => {p.style.transform='scale(.82)';});
    addEventListener('mouseup', () => {p.style.transform='scale(1)';});
  });
})();"""

async def main():
    random.seed(17)
    OUT.mkdir(parents=True, exist_ok=True)
    async with async_playwright() as pw:
        browser = await pw.chromium.launch(headless=True)
        ctx = await browser.new_context(viewport={'width':1440,'height':960},
            record_video_dir=str(OUT), record_video_size={'width':1440,'height':960})
        await ctx.add_init_script(POINTER)
        page = await ctx.new_page()
        page.set_default_timeout(12000)
        pos = [1150,750]

        async def pause(seconds=1):
            await page.wait_for_timeout(seconds*1000)

        async def point(locator):
            await locator.wait_for(state='visible')
            await locator.evaluate("e => {const r=e.getBoundingClientRect();if(r.top<70 || r.bottom>innerHeight-70)e.scrollIntoView({behavior:'smooth',block:'center'});}")
            await pause(.65)
            box = await locator.bounding_box()
            x,y = box['x']+box['width']*.48, box['y']+box['height']*.55
            sx,sy=pos
            for i in range(1,29):
                t=i/28; u=t*t*(3-2*t)
                await page.mouse.move(sx+(x-sx)*u,sy+(y-sy)*u+math.sin(t*math.pi)*9)
                await pause(.016)
            pos[:]=[x,y]
            await pause(.22)

        async def click(locator):
            await point(locator)
            await page.mouse.down(); await pause(.09); await page.mouse.up()
            await page.wait_for_load_state('domcontentloaded')
            await pause(.85)

        async def type_into(locator, text):
            await click(locator)
            for char in text:
                await page.keyboard.type(char)
                await pause(random.uniform(.045,.105) + (.12 if char in ',.' else 0))
            await pause(.55)

        async def shot(name):
            await page.screenshot(path=str(OUT / (name+'.png')))
            print(name,flush=True)

        await page.goto('http://127.0.0.1:8787/login')
        await pause(2)
        await click(page.get_by_role('button',name='Employee Raise tickets',exact=False))
        await pause(2); await shot('01-employee')
        await click(page.get_by_role('link',name='+ New ticket',exact=True))
        await type_into(page.locator('#title'),'Second monitor keeps flickering')
        await type_into(page.locator('#description'),'My second monitor flickers when I open a video call. I have checked the cable and restarted the laptop.')
        await click(page.get_by_role('button',name='Create ticket',exact=True))
        await pause(2); await shot('02-created')
        title='Second monitor keeps flickering'
        await click(page.get_by_role('button',name='Technician',exact=True))
        await pause(2); await shot('03-technician')
        await click(page.locator('nav a[href="/tickets"]'))
        await click(page.get_by_role('link',name=title,exact=True))
        await click(page.get_by_role('button',name='Assign to me',exact=True))
        await type_into(page.get_by_role('textbox',name='What fixed it?',exact=True),'Replaced the HDMI cable and tested a video call. Both screens are now stable.')
        await click(page.get_by_role('button',name='Mark resolved',exact=True))
        await pause(2); await shot('04-resolved')
        await click(page.get_by_role('button',name='Employee',exact=True))
        await click(page.get_by_role('link',name=title,exact=True))
        await pause(1.5)
        await click(page.get_by_role('button',name="Yes, it's fixed",exact=True))
        await pause(2); await shot('05-closed')
        await click(page.get_by_role('button',name='Admin',exact=True))
        await pause(2); await shot('06-admin')
        await click(page.locator('nav a[href="/assets"]'))
        available=page.locator('tbody tr').filter(has_text='Available').first.get_by_role('link')
        await click(available)
        await click(page.locator('#owner'))
        await page.keyboard.press('ArrowDown'); await pause(.6); await page.keyboard.press('Enter')
        await pause(.7)
        await click(page.get_by_role('button',name='Assign equipment',exact=True))
        await pause(1.6)
        await click(page.get_by_role('button',name='Return to stock',exact=True))
        await page.get_by_role('heading',name='Assignment history').evaluate("e=>e.scrollIntoView({behavior:'smooth',block:'center'})")
        await pause(2.5); await shot('07-history')
        video=page.video
        await ctx.close()
        await video.save_as(str(OUT/'walkthrough.webm'))
        await browser.close()

if __name__ == '__main__':
    asyncio.run(main())
