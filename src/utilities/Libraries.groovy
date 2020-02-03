package utilities

/* TOMCAT Deployment functions */
def deployToTomcat(servers,artifact,path) {
  for (server in servers.split(',')) {
    println "Deploying "+artifact+" to "+path+" context on "+server
    sh(script: 'curl -u $TOMCAT_CREDS "http://'+server+'/manager/text/deploy?update=true&path='+path+'" --upload-file iir.war')
  }
}

def getSettingsProperty(xml,deploy_env,key_name) {
  /* this function gets the value of a specified property for a specified environment
  from the settings.xml string that is passed in as the first argument:
      getSettingsProperty(xmlstring,environment_id,property_name)

  returns the text value of the property
  */
  def property_value="";
  def root = new XmlSlurper().parseText(xml);
  root.profiles.profile.each { profile ->
  if(profile.id.text() == deploy_env){
      profile.properties.'*'.each { property ->
          if(property.name()==key_name) {
              property_value=property.text();
          }
      }
    }
  }
  return property_value;
}

def getNexusVersions(Map opts = [:], String groupId, String artifactId) {
  /* returns a descending sorted list of versions for a provided groupId and artifactId 
    can optionally be limited to a set number of results by passing in a map with the key "limit" and value of the number of results you want to return.
    For example:

    getNexusVersions('com.cccis.devops','devops-flatfile-test-assembly',limit:5)

    will return 5 results for the devops-flatfile-test-assembly artifact
  */
  def baseNexusUrl = 'http://mavencentral.cccis.com/service/local/repositories/public/content/';
  def feedUrl = baseNexusUrl + groupId.replaceAll('\\.','/') + "/" + artifactId + "/";
  def versions=[]
  def xmlstr= new URL(feedUrl).getText()
  /* this function retrieves any subfolders at a given Nexus location. If the xml from the artifact level
  address is passed in, then this will return a list of all versions */
  def root = new XmlSlurper().parseText(xmlstr);
  root.data.'content-item'.each { ci ->
    if (ci.leaf.text() == 'false') {
      versions.add(ci.text.text())
    }
  }

  def sortedVersions=sortNexusVersions(versions);
  if (opts.containsKey('limit')) { 
    return sortedVersions[0..opts['limit']-1] 
    } else { 
      return sortedVersions
    }
}

@NonCPS
List sortNexusVersions(List versions) {
  // this is borrowed and modified from https://stackoverflow.com/a/7723766
  //List sorted_versions;
   List sorted_versions = [];
  sorted_versions=versions.sort { a, b -> 

    List verA = a.tokenize('.')
    List verB = b.tokenize('.')

    def commonIndices = Math.min(verA.size(), verB.size())

    for (int i = 0; i < commonIndices; ++i) {
      def numA = verA[i].split('-')[0].toInteger() // llebahn 10/10/2019 - split will handle '-SNAPSHOT' releases
      def numB = verB[i].split('-')[0].toInteger() 
      if (numA != numB) {
        return numA <=> numB
      }
    }

    // If we got this far then all the common indices are identical, so whichever version is longer must be more recent
    verA.size() <=> verB.size()
  }
  return sorted_versions.reverse()
}

@NonCPS
def getBuildUser() {
        return currentBuild.rawBuild.getCause(Cause.UserIdCause).getUserId()
}

def getGroups() {
  def user = getBuildUser()
  def groups = sh(script: "/home/build/common/ldap/getGroups.sh ${user}", returnStdout: true).split('\n')
    return groups
}

def getAuthorizedEnvironmentsForUser()
{ 
  /*  returns a string of environments that a user can deploy to, based on the membership in LDAP groups
    input: the list of groups that a user belongs to
    output: a list of environments that a user has access to deploy to
  */
  
  /* matrix of LDAP groups that are authorized to deploy to an environment */
  def matrix=[
          "aws": [
                  "dev6":["CI-ADMIN","CI-MIDDLEWARE","CI-DEV","CI-INT"],
                  "dev7":["CI-ADMIN","CI-MIDDLEWARE","CI-DEV","CI-INT"],
                  "dev8":["CI-ADMIN","CI-MIDDLEWARE","CI-DEV","CI-INT"],
                  "int6":["CI-ADMIN","CI-MIDDLEWARE","CI-DEV","CI-INT"],
                  "qa6":["CI-ADMIN","CI-qa"]
                ],
                "dc":[
                  "px":["CI-MIDDLEWARE","CI-ADMIN"],
                  "px2":["CI-MIDDLEWARE","CI-ADMIN"],
                  "ct":["CI-MIDDLEWARE","CI-ADMIN"]
                ],
                "prod":[
                  "prod":["CI-MIDDLEWARE","CI-ADMIN"],
                  "prod2":["CI-MIDDLEWARE"]
                ]
  ]
  
  
  def jenkins_loc=env.CCC_JENKINS_ENV;;
  def env_keys=matrix[jenkins_loc].keySet() as String[];
  def auth = getGroups();
  def auth_upper = auth.collect{ it.toUpperCase() }
  def auth_envs = [];
  for (env_name in env_keys)
  { 
    def inAuthKeys = env_name in auth_envs;
    if(!inAuthKeys) {
      for (ldapgroup in matrix[jenkins_loc][env_name])
      {
        
        def inLdapAuth = ldapgroup.toUpperCase() in auth_upper;
        if(inLdapAuth)
        {
          def notunique=env_name in auth_envs
            if (!notunique) {
            auth_envs.add(env_name);
        }
        }
      } 
    }
  }
  return auth_envs.join('\n');
}

def getMavenInput(mavenOpts,svnUrl) {
    def mvnType = input(
     id: 'mvnType', message: 'Maven action', parameters: [
     choice(name: "mvnChoice", choices: ['build','deploy','release'].join('\n'), description: "select the Maven action" )
    ])
    def commandInput=[];
    env.MVN_TYPE=mvnType;
    if(mvnType == "deploy") {
        def defaultVersion=getPomVersion(svnUrl);
         commandInput = input(
         id: 'deployInput', message: 'Perform Build Deployment', parameters: [
         choice(name: "deployEnv", choices: getAuthorizedEnvironmentsForUser(), description: "Environment" ),
         string(defaultValue: defaultVersion, description: 'Artifact Version', name: 'Version'),
         choice(name: "deployType", choices: ["Complete","Deploy","Undeploy","CacheOnly"].join('\n'), description: "Preparation Mode" )])
         commandInput.credentialsFile=generateTmpCredentialsFile(commandInput.deployEnv) 
         commandInput.mavenOpts=mavenOpts.deploy;    
         //println commandInput.credentialsFile    

         env.MVN_DEPLOY_ENV=commandInput.deployEnv;
         env.MVN_DEPLOY_VER=commandInput.Version;
         env.MVN_CREDS_FILE=commandInput.credentialsFile;
         env.MVN_DEPLOY_TYPE=commandInput.deployType;
    } else if (mvnType == "release") {
        def mavenVersion=getPomVersion(svnUrl)
        def releaseVersion=getReleaseVersion(mavenVersion)
        def developmentVersion=getDevelopmentVersion(mavenVersion)      
        commandInput = input(

         id: 'releaseInput', message: 'Deployment Environment', parameters: [
         string(defaultValue: releaseVersion, description: 'Release Version',name: 'releaseVersion'),
         string(defaultValue: developmentVersion, description: 'Development Version',name: 'developmentVersion'),
         string(defaultValue: '', description: 'Comment Prefix',name: 'commentPrefix') ])
         commandInput.mavenOpts=mavenOpts.release
         env.MVN_RELEASE_VER=commandInput.releaseVersion;
         env.MVN_DEV_VER=commandInput.developmentVersion;
         env.MVN_COMMENT_PREFIX=commandInput.commentPrefix;
    } else { 
        commandInput = ["mavenOpts":mavenOpts.build]
    }
     env.MVN_JOB_OPTS=commandInput.mavenOpts;
    //return [mvnType,commandInput]
}

def getRandomNumber(idLength)
{
        Random random = new Random()
        def fullstr=""
        def i=0
        while(i<=idLength)
        {
          a=random.nextInt(10).toString()
          fullstr+=a
          i++
        }
        return fullstr
}

def generateTmpCredentialsFile(deployEnv) {
  def randomNumber=getRandomNumber(16);

  def source="/home/build/credentials-"+deployEnv+".properties"
  def target="/tmp/creds"+randomNumber+".properties"
  new File(target) << new File(source).text
  return target;
}

def runMaven() {
  /*(mvnType
    , cmdInput) = getMavenInput(mavenOpts);*/
  def mvnType=env.MVN_TYPE;
  def mvncommand="";
  def mvntemplate="";
  def cmdInput=[];
  if (mvnType == "deploy") {
    cmdInput=['credentialsFile':env.MVN_CREDS_FILE,'deployEnv':env.MVN_DEPLOY_ENV,'deployType':env.MVN_DEPLOY_TYPE,'Version':env.MVN_DEPLOY_VER,'mavenOpts':env.MVN_JOB_OPTS]
    mvntemplate="mvn deployment:deploy-build -Ddeployment.credentials=$cmdInput.credentialsFile -Ddeployment.environment=$cmdInput.deployEnv -Ddeployment.prepMode=$cmdInput.deployType -Ddeployment.version=$cmdInput.Version $cmdInput.mavenOpts -U";
    def d_engine = new org.apache.commons.lang3.text.StrSubstitutor(cmdInput)
    mvncommand = d_engine.replace(mvntemplate)
  } else if (mvnType == "release") {
    cmdInput=['developmentVersion':env.MVN_DEV_VER,'releaseVersion':env.MVN_RELEASE_VER,'commentPrefix':env.MVN_COMMENT_PREFIX,'mavenOpts':env.MVN_JOB_OPTS]
    mvntemplate="mvn release:prepare release:perform -U -DdevelopmentVersion=$cmdInput.developmentVersion -DreleaseVersion=$cmdInput.releaseVersion \"-DscmCommentPrefix=$cmdInput.commentPrefix\" $cmdInput.mavenOpts";
    def r_engine = new org.apache.commons.lang3.text.StrSubstitutor(cmdInput)
    mvncommand = r_engine.replace(mvntemplate)
  } else {
    cmdInput=['mavenOpts':env.MVN_JOB_OPTS]
    mvntemplate="mvn $cmdInput.mavenOpts"
    def b_engine = new org.apache.commons.lang3.text.StrSubstitutor(cmdInput)
    mvncommand = b_engine.replace(mvntemplate)
  }
    /* run the actual Maven command */
    if (isUnix()) {
        sh "${mvncommand}" 
    } else {
        bat "${mvncommand}"
    }

}


def getPomFromSvn(svnurl) {
  // error out if pom doesn't exist?
  pomurl=svnurl+"/pom.xml"
  def xmlstr= new URL(pomurl).getText()
  return new XmlSlurper().parseText(xmlstr);
}

def getPomVersion(svnurl) {
  def root=getPomFromSvn(svnurl);
  if (root.version.size() == 1) {
    return root.version.text()
  } else if (root.parent.version.size() == 1) {
    return root.parent.version.text()
  }
}

def getReleaseVersion(version) {
  return version.split('-SNAPSHOT')[0]
}

def getDevelopmentVersion(version) {
  // this returns a development version for a release, i.e. -SNAPSHOT version
  // input is a version string from a pom file
  def verparts=version.split("-SNAPSHOT")[0].split("\\.");
  def lastpart=verparts[-1].toInteger()+1
  verparts[-1]=lastpart.toString()
  return verparts.join('.')+"-SNAPSHOT"
}


