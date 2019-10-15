#!/usr/bin/env groovy

// used environment variables
// GIT_URL
// GIT_BRANCH
// GIT_CREDENTIALS_ID
BUILD_CONFIG_NAME="${APP_NAME}-s2i-build"
// DEV_NAMESPACE
// TEST_NAMESPACE
// PROD_NAMESPACE
// BASE_IMAGESTREAM_NAMESPACE
// BASE_IMAGESTREAM_NAME
// BASE_IMAGE_TAG
TARGET_IMAGE_TAG=""
TARGET_IMAGESTREAM_NAME="${TARGET_IMAGESTREAM_NAME}"
// APP_DOMAIN
// BUILD_TIMEOUT
// DEPLOYMENT_TIMEOUT

pipeline {
    agent {
        label 'maven'
    }

  stages {

    stage('Init & Clone') {
      steps {
        sh 'rm -rf src && mkdir src'
        dir('src') {
          echo "${CLONE_BRANCH} - ${GIT_URL}"
          git branch: "${CLONE_BRANCH}", url: "${GIT_URL}"
        }
        script {
          def pom = readMavenPom file: 'pom.xml'
          APP_VERSION = (pom.version).replaceAll('-[A-Za-z]+', '')

          TARGET_IMAGE_TAG="${APP_VERSION}-${env.BUILD_NUMBER}"
        } // script
      } // steps
    } // stage

    stage('BUILD - Maven build') {
        steps {
            dir('src') {
                sh 'mvn clean package'
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
                                    '-p', "BASE_IMAGESTREAM_NAMESPACE=${BASE_IMAGESTREAM_NAMESPACE}",
                                    '-p', "BASE_IMAGESTREAM=${BASE_IMAGESTREAM}",
                                    '-p', "BASE_IMAGE_TAG=${BASE_IMAGE_TAG}",
                                    '-p', "TARGET_IMAGESTREAM=${TARGET_IMAGESTREAM_NAME}",
                                    '-p', "REVISION=release")

              openshift.create(build_obj)
            } // if

            bc.startBuild("--from-dir=src/target")
            def builds = bc.related('builds')
            // wait at most BUILD_TIMEOUT minutes for the build to complete
            timeout(BUILD_TIMEOUT.toInteger()) {
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
            createPvc(DEV_NAMESPACE, 'csv-data', APP_NAME, '1Gi')

            deployApplication(DEV_NAMESPACE, APP_NAME, 'dev', "${DEV_NAMESPACE}/${TARGET_IMAGESTREAM_NAME}:${TARGET_IMAGE_TAG}")

            createService(DEV_NAMESPACE, APP_NAME, 'dev')

            createSecureRoute(DEV_NAMESPACE, APP_NAME, '/csv', APP_DOMAIN)
          } // withProject
        } // script
      } // steps
    } // stage

    stage('DEV - Run tests') {
      steps {
        script {
          sleep 120
          testEndpointResponse("https://${APP_NAME}-${DEV_NAMESPACE}.${APP_DOMAIN}/csv/api/v1", 'DEV', 10, 30)
        } // script
      } // steps
    } // stage

    stage('DEV - Promote to TEST') {
        steps {
            script {
                openshift.withProject(DEV_NAMESPACE) {
                    openshift.tag("${DEV_NAMESPACE}/${TARGET_IMAGESTREAM_NAME}:${TARGET_IMAGE_TAG}", "${DEV_NAMESPACE}/${TARGET_IMAGESTREAM_NAME}:toTest")
                }
            } // script
        } // steps
    } // stage

    stage('TEST - Deploy') {
      steps {
        script {
          openshift.withProject(TEST_NAMESPACE) {
            openshift.tag("${DEV_NAMESPACE}/${TARGET_IMAGESTREAM_NAME}:toTest", "${TEST_NAMESPACE}/${TARGET_IMAGESTREAM_NAME}:${TARGET_IMAGE_TAG}")

            createPvc(TEST_NAMESPACE, 'csv-data', APP_NAME, '1Gi')

            deployApplication(TEST_NAMESPACE, APP_NAME, 'test', "${TEST_NAMESPACE}/${TARGET_IMAGESTREAM_NAME}:${TARGET_IMAGE_TAG}")

            createService(TEST_NAMESPACE, APP_NAME, 'test')

            createSecureRoute(TEST_NAMESPACE, APP_NAME, '/csv', APP_DOMAIN)
          } // withProject
        } // script
      } // steps
    } // stage

    stage('TEST - Run tests') {
      steps {
        script {
          sleep 120
          testEndpointResponse("https://${APP_NAME}-${TEST_NAMESPACE}.${APP_DOMAIN}/csv/api/v1", 'TEST', 10, 30)
        } // script
      } // steps
    } // stage

    stage('TEST - Promote to PROD') {
        steps {
            script {
                openshift.withProject(TEST_NAMESPACE) {
                    openshift.tag("${TEST_NAMESPACE}/${TARGET_IMAGESTREAM_NAME}:${TARGET_IMAGE_TAG}", "${TEST_NAMESPACE}/${TARGET_IMAGESTREAM_NAME}:toProd")
                }
            } // script
        } // steps
    } // stage

    stage('PROD - Deploy') {
      steps {
        script {
          openshift.withProject(PROD_NAMESPACE) {
            openshift.tag("${TEST_NAMESPACE}/${TARGET_IMAGESTREAM_NAME}:toProd", "${PROD_NAMESPACE}/${TARGET_IMAGESTREAM_NAME}:${TARGET_IMAGE_TAG}")

            createPvc(PROD_NAMESPACE, 'csv-data', APP_NAME, '1Gi')

            deployApplication(PROD_NAMESPACE, APP_NAME, 'prod', "${PROD_NAMESPACE}/${TARGET_IMAGESTREAM_NAME}:${TARGET_IMAGE_TAG}")

            createService(PROD_NAMESPACE, APP_NAME, 'prod')

            createSecureRoute(PROD_NAMESPACE, APP_NAME, '/csv', APP_DOMAIN)
          } // withProject
        } // script
      } // steps
    } // stage

    stage('PROD - Verify application') {
      steps {
        script {
          sleep 120
          testEndpointResponse("https://${APP_NAME}-${PROD_NAMESPACE}.${APP_DOMAIN}/csv/api/v1", 'PROD', 10, 30)
        } // script
      } // steps
    } // stage

  } // stages

} // pipeline

/**
 * Creates a service for the application based on file
 *
 * @param namespace namespace to create the service in
 * @param appName name of the application
 * @param env environment folder to use
 */
def createService(namespace, appName, env) {
    openshift.withProject(namespace) {
        def devSvc = openshift.selector('svc', appName)
        if(devSvc.exists()) {
            openshift.apply('-f', "src/openshift/objects/${env}/svc.yaml")
        } else {
            openshift.create('-f', "src/openshift/objects/${env}/svc.yaml")
        }
    }
}

/**
 * Deploys application to a given environment.
 *
 * @param namespace namespace to deploy application to
 * @param appName name of the application
 * @param env environment folder to user
 * @param image image to deploy
 */
def deployApplication(namespace, appName, env, image) {
    openshift.withProject(namespace) {
        def dc = openshift.selector('dc', appName)
        if(dc.exists()) {
            // apply from file
            openshift.replace('-f', "src/openshift/objects/${env}/deployment-config.yaml")
        } else {
            // create from file
            openshift.create('-f', "src/openshift/objects/${env}/deployment-config.yaml")
        }
        // patch image
        dcmap = devDc.object()
        dcmap.spec.template.spec.containers[0].image = "docker-registry.default.svc:5000/${image}"
        openshift.apply(dcmap)

        timeout(DEPLOYMENT_TIMEOUT.toInteger()) {
            def rm = devDc.rollout()
            rm.latest()
            rm.status()
        } // timeout
    }
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
                    '-p', "REVISION=release",
                    '-p', "SIZE=${size}")
            openshift.create(pvcObj)
        }
    } // withProject
}

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
                    '-p', "REVISION=release")
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
 * Creates a https route with a template to a given namespace if it does not exists
 *
 * @param namespace namespace to crete the route in
 * @param applicationName
 * @param contextRoot http context root for the application
 * @param appDomain openshift applications domain
 */
def createSecureRoute(namespace, applicationName, contextRoot, appDomain) {
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
