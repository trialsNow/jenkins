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

def getNexusAddress(groupId,artifactId,version,pkg) {
  def baseNexusUrl='http://mavencentral.cccis.com/service/local/repositories/public/content/'
  def feedUrl = baseNexusUrl + groupId.replaceAll('\\.','/') + "/" + artifactId + "/"+version+"/"+artifactId+'-'+version+'.'+pkg;
  return feedUrl;
}

def getNexusVersions(groupId,artifactId) {
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
  return versions;
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
                    "int6":["CI-ADMIN","CI-MIDDLEWARE","CI-INT"],
                    "qa6":["CI-ADMIN"]
                  ],
                  "dc":[
                    "px":["CI-MIDDLEWARE","CI-ADMIN"],
                    "px2":["CI-MIDDLEWARE","CI-ADMIN"],
                    "ct":["CI-MIDDLEWARE","CI-ADMIN"]
                  ],
                  "prod":[
                    "prod":["CI-MIDDLEWARE","CI-ADMIN"],
                    "prod2":["CI-MIDDLEWARE","CI-ADMIN"]
                  ]
    ]

  def jenkins_url=env.BUILD_URL.split('/')[2].split(':')[0];
  def jenkins_loc=""
  if (jenkins_url.contains('aws.cccis.com')) {
    jenkins_loc="aws";
  } else if (jenkins_url.contains('xa')) { 
    jenkins_loc="dc"
  } else {
    jenkins_loc="prod"
  }

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

def getMavenInput(mavenOpts) {
    def mvnType = input(
     id: 'mvnType', message: 'Maven action', parameters: [
     choice(name: "mvnChoice", choices: ['build','deploy','release'].join('\n'), description: "select the environment" )
    ])
    def commandInput=[];

    if(mvnType == "deploy") {
         commandInput = input(
         id: 'deployInput', message: 'Perform Build Deployment', parameters: [
         choice(name: "deployEnv", choices: getAuthorizedEnvironmentsForUser(), description: "Environment" ),
         string(defaultValue: '', description: 'Artifact Version', name: 'Version'),
         choice(name: "deployType", choices: ["Complete","Deploy","Undeploy","CacheOnly"].join('\n'), description: "Preparation Mode" )])
         commandInput.mavenOpts=mavenOpts.deploy
    } else if (mvnType == "release") {
      commandInput = input(
         id: 'releaseInput', message: 'Deployment Environment', parameters: [
         string(defaultValue: '', description: 'Release Version',name: 'releaseVersion'),
         string(defaultValue: '', description: 'Development Version',name: 'developmentVersion'),
         string(defaultValue: '', description: 'Comment Prefix',name: 'commentPrefix') ])
         commandInput.mavenOpts=mavenOpts.release
    } else { 
        commandInput = ["mavenOpts":mavenOpts.build]
    }
    return [mvnType,commandInput]
}

def runMaven(mavenOpts) {
  (mvnType, cmdInput) = getMavenInput(mavenOpts);
  println cmdInput;
  //println mavenOpts;
  def mvncommand="";
  def mvntemplate="";

  if (mvnType == "deploy") {
    mvntemplate="mvn deployment:deploy-build -Ddeployment.environment=$cmdInput.deployEnv -Ddeployment.prepMode=$cmdInput.deployType -Ddeployment.version=$cmdInput.Version $cmdInput.mavenOpts -U";
    def d_engine = new org.apache.commons.lang3.text.StrSubstitutor(cmdInput)
    mvncommand = d_engine.replace(mvntemplate)
  } else if (mvnType == "release") {
    mvntemplate="mvn release:prepare release:perform -U -DdevelopmentVersion=$cmdInput.developmentVersion -DreleaseVersion=$cmdInput.releaseVersion -DscmCommentPrefix=$cmdInput.commentPrefix $cmdInput.mavenOpts";
    def r_engine = new org.apache.commons.lang3.text.StrSubstitutor(cmdInput)
    mvncommand = r_engine.replace(mvntemplate)
  } else {
    mvntemplate="mvn $cmdInput.mavenOpts"
    def b_engine = new org.apache.commons.lang3.text.StrSubstitutor(cmdInput)
    mvncommand = b_engine.replace(mvntemplate)
  }
    /* run the actual Maven command */
    sh "${mvncommand}"
}





