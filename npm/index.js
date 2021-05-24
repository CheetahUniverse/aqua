#!/usr/bin/env node

"use strict";

const { exec } = require("child_process");
const path = require("path");
const fs = require('fs');

let importArgs = []

const nm = path.join(__dirname, "node_modules")
if (fs.existsSync(nm) && fs.lstatSync(nm).isDirectory()) {
  importArgs = ["-m", "node_modules"]
}

const args = [
  "java",
  "-jar",
  path.join(__dirname, "aqua-cli.jar"),
    ...importArgs,
  ...process.argv.slice(2),
];

const argsString = args.join(" ");

console.log(argsString);
exec(argsString, (err, stdout, stderr) => {
  console.error(stderr);
  console.log(stdout);

  if (err) {
    process.exit(err.code);
  }
});
