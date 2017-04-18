#! /usr/bin/python3

import glob
import os

jars = ":".join(glob.glob("jars/*"))
print(jars)
targets = " ".join(glob.glob("*.kt"))
os.system("kotlinc {targets} pawooHunter.kt -cp {jars} -include-runtime -d ./jars/scraper.jar".format(targets=targets, jars=jars) )
