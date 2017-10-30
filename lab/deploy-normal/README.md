# CSV Service Lab - Deploy Single Instance

#  Deploy CSV Service to Openshift

CSV service is an application that can read basic CSV files from a persistent
storage folder. The application has been created with Spring Boot.

---

## Lab Instructions

We are going to deploy a spring boot application that uses the OpenJDK 1.8 image
from Redhat registry.

After deploying the service we will configure the application deployment to
use various capabilities of the Openshift platform and demonstrate how the
platform manages the application for you.

__Note:__ For simplicity the instructions are written to use the oc command line client
but you can also accomplish all tasks via the console web gui.

__Note:__ In the instructions below replace _$username_ with your own username or initials.

### Login to Openshift

 oc login -u $username https://$your-ocp-master-host:8443/

### Create Project

Create a new project with the following command

 oc new-project $username-csv --display-name="$username csv"

You will automatically start using the freshly created project.

### Verify Base Image Availability

Check that the openjdk18 image is available:

 oc get is -n openshift | grep openjdk18

If the image is not available you need to import the image

 oc import-image redhat-openjdk18-openshift/openjdk18-openshift --from=registry.access.redhat.com/redhat-openjdk-18/openjdk18-openshift --confirm

### Building and Deploying the Application

We are using source-to-image (S2I) functionality to build the application.

First you need to create a new build:

 oc new-build -i openjdk18-openshift:latest --name=csv-s2i -l app=csv-service https://github.com/jmetso/csv-service.git

A build for the application is triggered automatically. You can follow the build
with 'oc logs -f bc/csv-s2i' or via the web console. After the build is
finished successfully we can deploy the application image:

 oc new-app -i csv-s2i --name=csv-service -l app=csv-service

### Expose a Route to the Application

You expose a route with the following command.

 oc expose service csv-service --hostname=csv-service-$username-csv.$appdomain --path=/csv/v1 -l app=csv-service

Verify that a route was created

 curl http://csv-service-$username-csv.$appdomain/csv/v1

or open the same url in a browser tab. You should get a Hello World! web page.

The route above uses both path based routing and domain based routing. If you try
to access 'http://csv-service-$username-csv.$appdomain/' you will get a _Application Not Available_ error.

### Create and Add a Persistent Volume to the Application

A persistent volume claim (or pvc) reserves a persistent volume (pv) for the
application. The claim is bound to a persistent volume. The contents of a
persistent volume will survive across pod restarts and the contents are shared
across all pods that use the same deployment configuration. The persistent
volume is bound to the application as long as the claim exists. When the claim
is deleted, the persistent volume will become inaccessible via OCP. Depending on the
platform configuration the contents of the persistent volume are either deleted
or preserved.

__NOTE:__ if the same claim is recreated it will get a fresh persistent volume
with no content.

There are two ways to create a persistent volume:

1. Create from a yaml-file: `oc create -f pvc/csv-claim.yaml`. Before you
create the claim edit the __claim name__ in the file to suit your csv-claim
and project name.

2. Use the web console: _Project Overview -> Storage -> Create Storage_. On the
page give it a __Name__ (csv-claim) and select __Access Mode__, in
this case _Single User (RWO)_ and give it a size of _5 MiB_.

Next we add the claim to the deployment configuration of the application above.
Again there are two options:

1. Mount the claim to the deployment configuration via command line:

 oc volume dc/csv-service --add --mount-path=/data --claim-name=csv-claim --name=data-volume

2. Using the web console: _Project Overview -> Applications -> Deployments -> csv-service -> Add storage_.
In the __Add Storage__ page select your storage (csv-claim) and give
it a mount path _/data_. You can leave the volume name empty. Finally click __Add__.

After you have added the storage, the application should redeploy automatically. If the application not redeploy automatically, trigger a new deployment manually.

__NOTE:__ Adding the persistent volume to a path will replace all contents of the folder with the contents of the persistent volume.

### Set environment variable to direct the application to the correct data folder

 oc set env dc/csv-service DATA_FOLDER=/data

This will cause the pod to redeploy automatically.

### Upload data to the persistent volume

After the pod is back up and running, download a data file:

 mkdir data

 curl https://raw.githubusercontent.com/jmetso/csv-service/master/src/test/resources/persons -o data/persons

Find the name of the pod:

 oc get pods | grep csv

Copy the pod name to the following command

 oc rsync data/ $podname:/data

Log in to the pod and see that the data file is in the correct folder

 oc rsh $podname

 ls /data

You should see the _person_ file in the folder.

### Demonstrate Persistence

To demonstrate persistence we are going to create another file outside the
persistent volume. Log in to the pod:

 oc rsh $podname

In the terminal, create file:

 touch /deployments/test.file

After you have created the file log out from the shell and kill the pod:

`oc delete pod $podname` or _Project Overview -> Applications -> Pods -> csv-service-number-hexstring (for example csv-service-2-649ox)-> Actions -> Delete_

Wait for a new pod to appear for the same application. This should also happen automatically.

Login to the new pod and look for the files:

 ls /data

 ls /home/jboss

You should see that file _/data/persons_ still exists, but file _/home/jboss/test.file_
does not. The second file disappeared because it is not part of the source image
created earlier in the build phase.

As a bonus round you can delete the persistent volume claim and recreate it.
Then restart the pod. Finally login to the pod and observer that the files
that we created previously have disappeared.

### Add resource limits

We will add the resource limits via the web console.

1. Login to the web console (http://$your-ocp-master-host:8443/console)
2. Choose your project ($username-csv)
3. On the Overview page navigate to _Applications -> Deployments -> csv-service_
4. Choose _Edit Resource Limits_ from _Actions_ drop down menu
5. Add a _Request_ of *100* millicores and a _Limit_ of *1000* millicores.
6. Click _Save_

You can see that the change to the deployment configuration has triggered
redeployment of the pod.

### Autoscaling

Now that we have added resource limits, we can add an autoscaler to the pod.
The autoscaler will scale the amount of pods up and down automatically based on
the load of the application. In this case we will use the amount of cpu as the
basis for the scaling. If the pod cpu use reaches 80% of the limit allocation, the
autoscaler will automatically start a new pod. When the cpu use will drop down,
the application will be scaled back. The maximum amount of pods allowed will be
two and the minumum one.

 oc autoscale dc/csv-service --cpu-percent=80 --max=2

To demonstrate autoscaling, the application is capable of generating load. trigger
the load generation for two minutes with the following url (with either curl or web browser):

 http://csv-service-$username-csv.$appdomain/csv/v1/load/120

Switch to project _Overview_ and follow that the number of pods for csv-service
will first scale up to two and the scale back down to one after two minutes.

### Health Checks

We will utilize the Spring boot actuator _/health_ end point for liveness check.

We will add health checks via the web console.

1. Login to the web console (http://$your-ocp-master-host:8443/console)
2. Choose your project ($username-csv)
3. On the Overview page navigate to _Applications -> Deployments -> csv-service_
4. Choose _Edit Health Checks_ from _Actions_ drop down menu
5. Click _Add readiness probe_
6. Verify that _Type_ is *HTTP GET*
7. Type */csv/v1/hello/readiness-probe* to _Path_
8. Verify that port is *8080*
9. Add an initial delay of *10* seconds
10. Click _Add liveness probe_
11. Verify that _Type_ is *HTTP GET*
12. Type */health* to _Path_
13. Verify that port is *8080*
14. Add an initial delay of *30* seconds
15. Click _Save_

The application will redeploy again due to the configuration change.

Now that we have added an availability probe, the platform will not allow traffic
into the pod until it is ready. To demonstrate this follow the steps below:

1. Navigate to _Overview_ page.
2. Click on the available pod.
3. Choose _Delete_ from the _Actions_ drop down menu
4. You will be taken to pods overview page.
5. Notice how the _Containers Ready_ column will stay as *0/1* a while after the
container has reached _Running_ status.

The liveness probe is monitoring that the pod is responsive. When the pod is no
longer responsive, the pod will be restarted automatically. Follow the steps below
to see it in practice:

1. Navigate to _Overview_ page.
2. Click on the available pod.
3. Note that _Restart Count_ is zero.
4. Open another tab in your browser and type
in _http://csv-service-$username-csv.$appdomain/csv/v1/makeUnhealthy_ as the
address and hit enter. This will cause the application status to switch to unhealthy and the
pod be restarted.
5. Switch back to the previous tab and observer the restart count increase to one.


### Clean up

Delete your project with:

 oc delete project $username-csv
