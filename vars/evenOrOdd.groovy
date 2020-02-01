def call(int buildNumber) {
pipeline {
      agent any
	  tools { 
        maven 'Maven 3.6.2' 
        jdk 'jdk8' 
    }
  if (buildNumber % 2 == 0) {
    
      stages {
        stage('Even Stage') {
          steps {
            echo "The build number is even"
          }
        }
      }
    
  } else {
    
      stages {
        stage('Odd Stage') {
          steps {
            echo "The build number is odd"
          }
        }
      }
    }
  }
}