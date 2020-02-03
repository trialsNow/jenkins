def lib = new utilities.Libraries()

def mavenOpts=[
    "release": "",
    "build": "clean install -Dpre-site-goals=clean,deploy ",
    "deploy": "" ]
	

  
def call(String svnURL) {
  
pipeline {
    agent any
    tools {
        maven 'Maven 3.6.2'
        jdk 'jdk8'
    }
   
    stages {
        stage("Select Build/Deployment Type") {
            steps {
                script {
                    lib.getMavenInput(mavenOpts,svnURL)
                }
            }
        }
        stage("Check out from SVN") {
            steps {
                svn svnURL
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