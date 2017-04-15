import os
import sys

with open('forbes400_2017.txt') as f:
  for line in f:
    line = line.strip()
    ents = line.split('\t')
    name  = ents[0]
    money = ents[1].replace('$', '').replace(' B', '')
    print("{name} {money}".format(name=name, money=money) )
