@Library("CICD-Jenkins-Libraries@master")

def lib = new utilities.Libraries()

def mavenOpts=[
    "release": "",
    "build": "clean install -Dpre-site-goals=clean,deploy ",
    "deploy": "" ]
  
def call(String svnURL) {
  
pipeline {
    agent { label 'linux'}
    tools {
        maven 'Maven 3.0.5'
        jdk 'JDK 1.7.0_51'
    }
    environment {
        JOB_SVN_URL=svnURL
        
    }
    stages {
        stage("Select Build/Deployment Type") {
            steps {
                script {
                    lib.getMavenInput(mavenOpts,env.JOB_SVN_URL)
                }
            }
        }
        stage("Check out from SVN") {
            steps {
                svn "$env.JOB_SVN_URL"
            }
        }
        stage('Maven Build/Deploy') {
            steps {
                script {
                    lib.runMaven()
                }
            }
        }
    }
}
}