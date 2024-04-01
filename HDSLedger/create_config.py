#!/bin/env python3

import json
import sys

def modify_ports(input_file, output_file):
  with open(input_file, 'r') as f:
    data = json.load(f)

  entities = len(data['nodes']) + len(data['clients'])

  # Get max port
  for node in data['nodes']:
    node['port'] += entities 
  for client in data['clients']:
    client['port'] += entities 

  # Generate new filename with incremented suffix
  with open(output_file, 'w') as f:
    json.dump(data, f, indent=4)  # Write to new file with indentation

  print(f"New file created: {output_file}")

if len(sys.argv) < 3:
    print("Please provide input file and output")
    exit()

input_file = sys.argv[1]
output_file = sys.argv[2]

modify_ports(input_file, output_file)

