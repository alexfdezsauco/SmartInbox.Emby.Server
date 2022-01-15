#addin nuget:?package=Cake.Docker&version=1.1.0
#addin nuget:?package=Cake.FileHelpers&version=5.0.0

#load "config.cake"

var target = Argument("target", "Pack");
var buildConfiguration = Argument("Configuration", "Release");

var dockerRepositoryProxy = EnvironmentVariable("DOCKER_REPOSITORY_PROXY") ?? "docker.io";
var dockerRepository = EnvironmentVariable("DOCKER_REPOSITORY") ?? string.Empty;
var mavenRepositoryProxy = EnvironmentVariable("MAVEN_REPOSITORY_PROXY") ?? "https://repo1.maven.org/maven2/";
var DockerRepositoryPrefix = string.IsNullOrWhiteSpace(dockerRepository) ? string.Empty : dockerRepository + "/";

Task("UpdateVersion")
  .Does(() =>
  {
      StartProcess("dotnet", new ProcessSettings
      {
          Arguments = new ProcessArgumentBuilder()
            .Append("gitversion")
            .Append("/output")
            .Append("buildserver")
            .Append("/nofetch")
            .Append("/updateassemblyinfo")
      });

      IEnumerable<string> redirectedStandardOutput;
      StartProcess("dotnet", new ProcessSettings
      {
          Arguments = new ProcessArgumentBuilder()
            .Append("gitversion")
            .Append("/output")
            .Append("json")
            .Append("/nofetch"),
          RedirectStandardOutput = true
      }, out redirectedStandardOutput);

      NuGetVersionV2 = redirectedStandardOutput.FirstOrDefault(s => s.Contains("NuGetVersionV2")).Split(':')[1].Trim(',', ' ', '"');
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
      for(int i = 0; i < DockerFiles.Length; i++)
      {
        var outputImage = OutputImages[i];
        var dockerFile = DockerFiles[i];

        var settings = new DockerImageBuildSettings() 
                          {
                              File = dockerFile,
                              BuildArg = new [] {$"DOCKER_REPOSITORY_PROXY={dockerRepositoryProxy}", 
                                                $"MAVEN_REPOSITORY_PROXY={mavenRepositoryProxy}", 
                                                $"PACKAGE_VERSION={NuGetVersionV2}"},
                              Tag = new[] {$"{DockerRepositoryPrefix}{outputImage}:{NuGetVersionV2}", $"{DockerRepositoryPrefix}{outputImage}:latest"},
                              // Network="nexus"
                          };
        DockerBuild(settings, srcFilePath.FullPath);
        
      }
  });

RunTarget(target);