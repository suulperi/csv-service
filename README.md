# csv-service

A very simple Spring Boot application to demonstrate various Openshift functionalities in demos and workshops.

Parses csv files and returns the content as JSON.

CSV file format:
id, firstname, lastname
1, Janne, Metso

Requires env variable DATA_FOLDER. This points the service to a folder that contains the CSV files. 
