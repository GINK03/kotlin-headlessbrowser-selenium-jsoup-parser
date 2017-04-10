import json

i = json.load(open('./url_details.json', 'r'))
for k, v in i.items():
  print(k, v)
