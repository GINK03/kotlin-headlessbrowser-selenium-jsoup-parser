import os
import sys

if '--mstdn' in sys.argv:
  while True:
    os.system('./run.scraper.py mstdnHunter th=2')

if '--pawoo' in sys.argv:
  while True:
    os.system('./run.scraper.py pawooHunter th=2')
