def call(int buildNumber) {
pipeline {
      agent any
	  tools { 
        maven 'Maven 3.6.2' 
        jdk 'jdk8' 
    }
  
    
      stages {
	  if (buildNumber % 2 == 0) {
        stage('Even Stage') {
          steps {
            echo "The build number is even"
          }
        }
      }
	  else {
	  stage('Odd Stage') {
          steps {
            echo "The build number is odd"
          }
        }
	  }
	  }
    
  } 
}