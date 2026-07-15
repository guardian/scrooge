{ sources ? import ./nix/sources.nix }:
let
  pkgs = import sources.nixpkgs { };
  guardianNix = builtins.fetchGit {
    url = "git@github.com:guardian/nix-development-environment.git";
    ref = "refs/tags/v1";
  };
  guardianDev = import "${guardianNix.outPath}/guardian-dev.nix" pkgs;
  # guardianDev = import ../guardian-nix/guardian-dev.nix pkgs;

  sbt = pkgs.sbt.override { jre = pkgs.corretto11; };
  metals = pkgs.metals; # .override { jre = pkgs.zulu11; };

  # Publishes a local version of scrooge, including sbt plugin.
  # copied from dodo-build.sh
  publishLocal = pkgs.writeShellApplication {
    name = "publish-local";
    runtimeInputs = [ sbt ];
    text = ''
      sbt +scrooge-generator/publishLocal
      sbt +scrooge-publish-local/publishLocal
      sbt ++2.10.7 ^^0.13.18 scrooge-sbt-plugin/publishLocal
      sbt "project scrooge-sbt-plugin" ++2.12.12 ^^1.12.13 publishLocal
    '';
  };

in guardianDev.devEnv {
  name = "scattergood";
  commands = [];
  extraInputs =
    [ pkgs.zulu11
      metals
      sbt
      pkgs.scala_2_13
      publishLocal
    ];
}
