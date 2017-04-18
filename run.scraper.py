#! /usr/bin/python3
import glob
import os
import sys
jars = ":".join(glob.glob("jars/*"))
print(jars)
os.system("kotlin -cp {jars} ScraperKt {args}".format(jars=jars, args=" ".join(sys.argv[1:])) )
