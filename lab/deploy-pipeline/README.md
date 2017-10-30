# CSV Service Lab - Deploy with a Jenkins Pipeline

#  Deploy CSV Service to Openshift

CSV service is an application that can read basic CSV files from a persistent
storage folder. The application has been created with Spring Boot.

---

## Lab Instructions

In this lab we deploy the CSV Service via a Jenkins pipeline. The CSV service uses
the OpenJDK 1.8 image from Redhat registry.

### Login to Openshift

`oc login -u $username https://$your-ocp-master-host:8443/`

### Create and Prepare Projects

First, we will create three projects to simulate develoment, test, and production
environments:

`oc new-project csv-service-dev --display-name="CSV Dev"`
`oc new-project csv-service-test --display-name="CSV Test"`
`oc new-project csv-service-prod --display-name="CSV Prod"`

Second, we will allow the test project to pull images from the development project
and the production project to pull images from the test project. To do that execute
the commands below:

`oc policy add-role-to-user system:image-puller system:serviceaccount:csv-service-test:default -n csv-service-dev`

`oc policy add-role-to-user system:image-puller system:serviceaccount:csv-service-prod:default -n csv-service-test`


Finally switch back to the dev project:

`oc project csv-service-dev`

__Note:__ In the current revision of this lab, the pipeline depends on the names
of the projects to be exactly as above. In a future version the pipeline will be
created with a template that will allow customization of the project names.

### Verify Base Image Availability

Check that the openjdk18 image is available:

`oc get is -n openshift | grep openjdk18`

If the image is not available you need to import the image

`oc import-image redhat-openjdk18-openshift/openjdk18-openshift --from=registry.access.redhat.com/redhat-openjdk-18/openjdk18-openshift --confirm -n csv-service-dev`


### Building and Deploying the Application

We are using source-to-image (S2I) functionality to build the application.

First you need to create a new build:

`oc new-build --strategy=pipeline --name=csv-pipeline -l pipeline=csv https://github.com/jmetso/csv-service.git --context-dir=pipeline -n csv-service-dev`

Since we are using a Jenkins pipeline to do the build, we will need a Jenkins instance
to run the pipeline. Fortunately such an image exists and the Openshift platform
will automatically spin up a Jenkins for us (unless a Jenkins is already running).

Log in to the web console to see progress of the pipeline:
1. Login to the web console (http://$your-ocp-master-host:8443/console)
2. Choose your project ($username-csv)
3. Watch the _Overview_ page as the Jenkins instace is spun up.
4. After the Jenkins instance is up and running navigate to _Builds -> Pipelines_ and watch the pipeline run.

If the pipeline does not start, follow the steps below:
1. Navigate _Overview -> Builds -> Pipelines -> csv-pipeline -> Build #number_ and click _Cancel Build_
2. Navigate bacl to csv-pipeline and click _Start Pipeline_

The pipeline will fail in the step _Deploy to Test_. This is because Jenkins
needs access to the test and  production projects to create required objects.
We will allow this by adding specific rights for the jenkins service account. A
service account is a technical account inside Openshift that allows administrators
to give permissions to applications. We will allow Jenkins to _edit_ the objects in
the above mentioned projects:

`oc policy add-role-to-user edit system:serviceaccount:csv-service-dev:jenkins -n csv-service-test`
`oc policy add-role-to-user edit system:serviceaccount:csv-service-dev:jenkins -n csv-service-prod`

After adding the permissions, run the pipeline again:

Navigate _Overview -> Builds -> Pipelines -> csv-pipeline_ and click _Start Pipeline_.

The pipeline should run all the way until step _Tag image to prod ready_. If it
does not, check and verify that you have followed all the steps in *Create and Prepare Projects*
exactly as specified.

At the step _Tag image to prod ready_ the pipeline will pause and wait for the
user to approve that the image is indeed production ready. To approve the image
click the link in the step and choose _Proceed_. As a result the application is
deployed to production.

### Expose a Route to the Application

We will expose a route to the production application with the following command:

`oc expose service csv-service --hostname=csv-service-csv-service-prod.$appdomain --path=/csv/v1 -l app=csv-service -n app=csv-service-prod`

Verify that a route was created:

`curl http://csv-service-csv-service-prod.$appdomain/csv/v1`

or open the same url in a browser tab. You should get a Hello World! web page.

The route above uses both path based routing and domain based routing. If you try
to access 'http://csv-service-$username-csv.$appdomain/' you will get a _Application Not Available_ error.

### Clean up

Delete your projects with:

`oc delete project csv-service-dev`
`oc delete project csv-service-test`
`oc delete project csv-service-prod`
