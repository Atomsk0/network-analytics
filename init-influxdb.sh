#!/bin/bash

INFLUX_HOST="http://localhost:8086"
INFLUX_ORG="myorg"
INFLUX_BUCKET="mybucket"
INFLUX_TOKEN="mytoken"

echo "Waiting for InfluxDB to start..."
sleep 10

# Check if InfluxDB setup has already been completed
if influx ping -host $INFLUX_HOST; then
  echo "InfluxDB is up and running"

  # Use the setup token for initial setup
  echo "Using setup token for initial setup..."
  influx setup --host $INFLUX_HOST --username myusername --password mypassword --org $INFLUX_ORG --bucket $INFLUX_BUCKET --token $INFLUX_TOKEN --force

  # Create additional resources if needed
  echo "Creating additional resources..."
  influx bucket create -n $INFLUX_BUCKET -o $INFLUX_ORG -t $INFLUX_TOKEN
  influx user create -n myusername -o $INFLUX_ORG --password mypassword -t $INFLUX_TOKEN
  influx auth create -o $INFLUX_ORG -u myusername --read-buckets --write-buckets -t $INFLUX_TOKEN

  echo "Initialization script completed."
else
  echo "InfluxDB setup has already been completed or failed to start"
fi
