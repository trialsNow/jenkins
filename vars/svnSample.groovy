def call(String svnURL) {
  pipeline {
      agent any
	  tools { 
        maven 'Maven 3.6.2' 
        jdk 'jdk8' 
    }
	stages {
        stage('Print SVN URL') {
          steps {
            echo "svURL:"+svnURL
          }
        }
      }
    }
}