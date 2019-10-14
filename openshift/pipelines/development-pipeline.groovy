#!/usr/bin/env groovy

// used environment variables
// GIT_URL
// GIT_CREDENTIALS_ID
APP_NAME="dev-${APP_NAME}"
BUILD_CONFIG_NAME="${APP_NAME}-s2i-build"
// DEV_NAMESPACE
// BASE_IMAGESTREAM_NAMESPACE
// BASE_IMAGESTREAM_NAME
// BASE_IMAGE_TAG
TARGET_IMAGE_TAG=""
TARGET_IMAGESTREAM_NAME="dev-${TARGET_IMAGESTREAM_NAME}"
// APP_DOMAIN
// BUILD_TIMEOUT
// DEPLOYMENT_TIMEOUT

pipeline {
  agent any

  stages {

    stage('Init') {
      steps {
        sh 'rm -rf src && mkdir src'
      } // steps
    } // stage

    stage('Clone') {
      steps {
        dir('src') {
          git branch: 'development', url: "${GIT_URL}", credentialsId: "${GIT_CREDENTIALS_ID}"
        }
      } // steps
    } // stage

    stage('Configure') {
      steps {
        script {
          def pom = readMavenPom file: 'pom.xml'
          APP_VERSION = (pom.version)

          TARGET_IMAGE_TAG="${APP_VERSION}-${env.BUILD_NUMBER}"
        }
      } // steps
    } // stage

    stage('BUILD - Maven build') {
        steps {
            dir('src') {
                sh 'mvn clean deploy'
            }
        } // steps
    } // stage

    stage('BUILD - Bake application image') {
      steps {
        script {
          openshift.withProject(DEV_NAMESPACE) {

            createImageStream(TARGET_IMAGESTREAM_NAME, APP_NAME, DEV_NAMESPACE)

            def bc = openshift.selector("bc/${BUILD_CONFIG_NAME}")
            if(!bc.exists()) {
              def build_obj = openshift.process(readFile(file:'src/openshift/templates/binary-s2i-template.yaml'),
                                    '-p', "APP_NAME=${APP_NAME}",
                                    '-p', "NAME=${BUILD_CONFIG_NAME}",
                                    '-p', "BASE_IMAGESTREAM_NAMESPACE=${SRC_IMAGESTREAM_NAMESPACE}",
                                    '-p', "BASE_IMAGESTREAM=${SRC_IMAGESTREAM}",
                                    '-p', "BASE_IMAGE_TAG=${SRC_IMAGE_TAG}",
                                    '-p', "TARGET_IMAGESTREAM=${TARGET_IMAGESTREAM_NAME}",
                                    '-p', "REVISION=development")

              openshift.create(build_obj)
            } // if

            bc.startBuild("--from-dir=src/target")
            def builds = bc.related('builds')
            // wait at most BUILD_TIMEOUT minutes for the build to complete
            timeout(BUILD_TIMEOUT) {
              builds.untilEach(1) {
                return it.object().status.phase == 'Complete'
              }
            } // timeout

            openshift.tag("${DEV_NAMESPACE}/${TARGET_IMAGESTREAM_NAME}:latest", "${DEV_NAMESPACE}/${TARGET_IMAGESTREAM_NAME}:${TARGET_IMAGE_TAG}")
          } // withProject
        } // script 
      } // steps
    } // stage

    stage('DEV - Deploy') {
      steps {
        script {
          openshift.withProject(DEV_NAMESPACE) {
            createPvc(DEV_NAMESPACE, 'dev-csv-data', APP_NAME, '1Gi')

            def devDc = openshift.selector('dc', APP_NAME)
            if(devDc.exists()) {
                // apply from file
                openshift.create('-f', 'src/openshift/objects/dev/dev-deployment-config.yaml')
            } else {
                // create from file
                openshift.replace('-f', 'src/openshift/objects/dev/dev-deployment-config.yaml')
            }
            // patch image
            dcmap = dc.object()
            dcmap.spec.template.spec.containers[0].image = "openshift.docker-registry.default.svc:5000/${DEV_NAMESPACE}/${TARGET_IMAGESTREAM_NAME}:${TARGET_IMAGE_TAG}"
            openshift.apply(dcmap)

            createSecureRoute(DEV_NAMESPACE, APP_NAME, '/csv', APP_DOMAIN)
          } // withProject
        } // script
      } // steps
    } // stage

    stage('DEV - Run tests') {
      steps {
        script {
          sleep 120
          testEndpointResponse("https://${APP_NAME}-${DEV_NAMESPACE}.${APP_DOMAIN}/csv", 'jmetso', 60, 60)
        } //
      } // steps
    } // stage

  } // stages

} // pipeline


/**
 * Create image stream if one does not exist
 *
 * @param name name of imagestream to create
 * @param appName name of application, for labeling the imagestream
 * @param namespace project/namespace name
 * @param cluster cluster id
 */
def createImageStream(name, appName, namespace) {
    openshift.withProject(namespace) {
        def is = openshift.selector('is', name);
        if(!is.exists()) {
            def isObj = openshift.process(readFile(file:'src/openshift/templates/imagestream-template.yaml'), 
                    '-p', "APP_NAME=${appName}", 
                    '-p', "IMAGESTREAM_NAME=${name}", 
                    '-p', "REVISION=development")
            openshift.create(isObj)
        }
    } // withProject
}

/**
 * Test that http page from url contains given text
 *
 * @param url endpoint url
 * @param text text to search for
 * @param wait number of minutes to wait until timeout, default is 10
 * @param pollInterval number of seconds to sleep between retries, default is 30
 */
def testEndpointResponse(url, text, wait=10, pollInterval=30) {
  def cont = true
  timeout(wait) {
    while(cont) {
      def response = sh script:"curl -s -k ${url}", returnStdout: true
      if(response.contains("${text}")) {
        cont = false
        echo "Success!"
      } else {
        sleep pollInterval
      }
    } // while
  } // timeout
}

/**
 * Creates a persistent volume claim (pvc)
 * 
 * @param namespace namespace to create the pvc in
 * @param name name of pvc
 * @param appName name of application
 * @param size size of pvc
 */
def createPvc(namespace, name, appName, size) {
    openshift.withProject(namespace) {
        def pvc = openshift.selector('pvc', name)
        if(!pvc.exists()) {
            def pvcObj = openshift.process(readFile(file:'src/openshift/templates/pvc-template.yaml'),
                    '-p', "NAME=${name}",
                    '-p', "APP_NAME=${appName}",
                    '-p', "REVISION=development",
                    '-p', "SIZE=${size}")
            openshift.create(pvcObj)
        }
    } // withProject
}

/**
 * Creates a https route with a template to a given namespace if it does not exists
 *
 * @param namespace namespace to crete the route in
 * @param applicationName
 * @param contextRoot http context root for the application
 * @param appDomain openshift applications domain
 */
def call(namespace, applicationName, contextRoot, appDomain) {
    openshift.withProject(namespace) {
        def route = openshift.selector('route', "${applicationName}-secure");
        if(!route.exists()) {
            def routeObj = openshift.process(readFile(file:'src/openshift/templates/secure-route-template.yaml'), 
                    '-p', "APP_NAME=${applicationName}",
                    '-p', "APP_NAMESPACE=${namespace}", 
                    '-p', "APP_CONTEXT_ROOT=${contextRoot}", 
                    '-p', "APP_DOMAIN=${appDomain}")
            openshift.create(routeObj)
        }
    }
}