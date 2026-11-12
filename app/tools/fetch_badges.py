"""Fetch display-only club crests and national flags; never modifies MySQL."""
import concurrent.futures
import io
import json
from pathlib import Path
import re
import unicodedata
import urllib.request
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
WEB=ROOT/'app/web'
LEAGUES=['eng.1','eng.2','ger.1','bel.1','par.1']
FLAGS={
 'Argentina':('ar','Argentina'),'Belgium':('be','Bỉ'),'Brazil':('br','Brazil'),
 'Cameroon':('cm','Cameroon'),'Côte d’Ivoire':('ci','Bờ Biển Ngà'),
 'Denmark':('dk','Đan Mạch'),'England':('gb-eng','Anh'),'France':('fr','Pháp'),
 'Morocco':('ma','Morocco'),'Netherlands':('nl','Hà Lan'),'Paraguay':('py','Paraguay'),
 'Portugal':('pt','Bồ Đào Nha'),'Republic of Ireland':('ie','Ireland'),
 'Scotland':('gb-sct','Scotland'),'Slovenia':('si','Slovenia'),
 'Türkiye':('tr','Thổ Nhĩ Kỳ'),'Uruguay':('uy','Uruguay')
}
def slug(value):
 value=unicodedata.normalize('NFD',value).lower().replace('ı','i')
 return re.sub('[^a-z0-9]+','-', ''.join(c for c in value if not unicodedata.combining(c))).strip('-')
def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'}),timeout=25) as r:return r.read()
def league(code):
 url=f'https://site.api.espn.com/apis/site/v2/sports/soccer/{code}/teams?limit=100'
 return [(t['team'],url) for t in json.loads(get(url))['sports'][0]['leagues'][0]['teams']]
def main():
 for kind in ['crests','flags']:(WEB/kind).mkdir(exist_ok=True)
 seed=(ROOT/'db/04_seed.sql').read_text('utf8').split('INSERT INTO CauLacBo(',1)[1].split(';',1)[0]
 clubs=re.findall(r"\(\d+,'([^']+)'",seed)+['Hull City AFC','Coventry City FC','Ipswich Town FC']
 teams={};ids={}
 with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
  for rows in pool.map(league,LEAGUES):
   for team,source in rows:teams[slug(team['displayName'])]=(team,source);ids[team['id']]=(team,source)
 aliases={'Royal Antwerp':'Antwerp','Hull City AFC':'Hull City','Coventry City FC':'Coventry City','Ipswich Town FC':'Ipswich Town'}
 assets={'clubs':{},'flags':{},'sources':{}}
 jobs=[]
 for name in clubs:
  team,source=ids['2671'] if name=='Cerro Porteño' else teams[slug(aliases.get(name,name))]
  src=team['logos'][0]['href'];dest='/crests/'+slug(name)+'.webp'
  assets['clubs'][name]=dest;assets['sources'][dest]={'image':src,'page':source,'owner':'Respective football club / ESPN'};jobs.append((src,dest,96))
 for name,(code,label) in FLAGS.items():
  src='https://flagcdn.com/w80/'+code+'.png';dest='/flags/'+code+'.webp'
  assets['flags'][name]={'src':dest,'name':label};assets['sources'][dest]={'image':src,'page':'https://flagpedia.net/','owner':'Flagpedia / FlagCDN'};jobs.append((src,dest,80))
 def download(job):
  src,dest,size=job;image=Image.open(io.BytesIO(get(src))).convert('RGBA');image.thumbnail((size,size));image.save(WEB/dest.lstrip('/'),'WEBP',quality=94)
 with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:list(pool.map(download,jobs))
 (WEB/'football-assets.js').write_text("'use strict';\nwindow.FOOTBALL_ASSETS=Object.freeze("+json.dumps(assets,ensure_ascii=False,indent=2)+');\n','utf8')
 print(f"Downloaded {len(assets['clubs'])} club crests and {len(assets['flags'])} national flags.")
if __name__=='__main__':main()
