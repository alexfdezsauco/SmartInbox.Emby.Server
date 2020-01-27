#addin "Cake.Docker"
#addin "Cake.FileHelpers"

#load "config.cake"

var target = Argument("target", "Pack");
var buildConfiguration = Argument("Configuration", "Release");

using System.Net;
using System.Net.Sockets;

string localIpAddress;
using (var socket = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, 0))
{
    socket.Connect("8.8.8.8", 65530);
    var endPoint = socket.LocalEndPoint as IPEndPoint;
    localIpAddress = endPoint.Address.ToString();
}

var dockerRepositoryProxy = EnvironmentVariable("DOCKER_REPOSITORY_PROXY") ?? $"{localIpAddress}:2500";
var dockerRepository = EnvironmentVariable("DOCKER_REPOSITORY") ?? string.Empty;
var nugetRepositoryProxy = EnvironmentVariable("MAVEN_REPOSITORY_PROXY") ?? $"http://{localIpAddress}:8081/repository/maven-public/";
var DockerRepositoryPrefix = string.IsNullOrWhiteSpace(dockerRepository) ? string.Empty : dockerRepository + "/";

Setup (context => {
  context.Tools.RegisterFile("./tools/GitVersion.CommandLine/tools/GitVersion.exe");
});

Task ("UpdateVersion")
  .Does (() => {
    FilePath gitVersionPath = Context.Tools.Resolve ("GitVersion.exe");
    StartProcess (gitVersionPath, new ProcessSettings {
      Arguments = new ProcessArgumentBuilder ()
        .Append ("/output")
        .Append ("buildserver")
        .Append ("/nofetch")
        .Append ("/updateassemblyinfo")
    });

    IEnumerable<string> redirectedStandardOutput;
    StartProcess (gitVersionPath, new ProcessSettings {
      Arguments = new ProcessArgumentBuilder ()
        .Append ("/output")
        .Append ("json"),
        RedirectStandardOutput = true
    }, out redirectedStandardOutput);

    NuGetVersionV2 = redirectedStandardOutput.FirstOrDefault (s => s.Contains ("NuGetVersionV2")).Split (':') [1].Trim (',').Trim ('"');
  });


Task ("DockerBuild")
  .IsDependentOn ("UpdateVersion")
  .Does (() => 
  {
      if(DockerFiles.Length != OutputImages.Length)
      {
        Error("DockerFiles.Length != OutputImages.Length");
      }

      var srcFilePath = GetDirectories("src").FirstOrDefault();
      // var tarFileName = "java.pom.tar.gz";
      // var files = GetFiles(srcFilePath + "/**/pom.xml");
      // foreach(var file in files)
      // {
      //   var relativeFilePath = srcFilePath.GetRelativePath(file);
      //   using(var process = StartAndReturnProcess("tar", new ProcessSettings{ Arguments = $"-rf {tarFileName} -C src {relativeFilePath}"}))
      //   {
      //       process.WaitForExit();
      //   }
      // }
      
      for(int i = 0; i < DockerFiles.Length; i++)
      {
        var outputImage = OutputImages[i];
        var dockerFile = DockerFiles[i];

        var settings = new DockerImageBuildSettings() 
                          {
                              File = dockerFile,
                              BuildArg = new [] {$"DOCKER_REPOSITORY_PROXY={dockerRepositoryProxy}", 
                                                $"MAVEN_REPOSITORY_PROXY={nugetRepositoryProxy}", 
                                                $"PACKAGE_VERSION={NuGetVersionV2}"},
                              Tag = new[] {$"{DockerRepositoryPrefix}{outputImage}:{NuGetVersionV2}", $"{DockerRepositoryPrefix}{outputImage}:latest"}
                          };
        DockerBuild(settings, srcFilePath.FullPath);
}
  });

RunTarget(target);