def call(int buildNumber) {
  if (buildNumber % 2 == 0) {
    pipeline {
      agent any
	  tools { 
        maven 'Maven 3.6.2' 
        jdk 'jdk8' 
    }
  
      stages {
        stage('Even Stage') {
          steps {
            echo "The build number is even"
          }
        }
      }
    }
  } else {
    pipeline {
      agent any
	  tools { 
        maven 'Maven 3.6.2' 
        jdk 'jdk8' 
    }
  
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
