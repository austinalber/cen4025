Vagrant.configure("2") do |config|
  config.vm.boot_timeout = 900
  config.vm.box = "ubuntu/jammy64"
  config.vm.box_version = "20241002.0.0"

  config.vm.synced_folder ".", "/vagrant",
    type: "virtualbox",
    owner: "vagrant",
    group: "vagrant",
    mount_options: ["dmode=775", "fmode=664"]

  config.vm.network "forwarded_port",
    guest: 8080,
    host: 18080,
    host_ip: "127.0.0.1"

  config.vm.provider "virtualbox" do |virtualbox|
    virtualbox.name = "club-reservation-api"
    virtualbox.cpus = 2
    virtualbox.memory = 2048
  end

  config.vm.provision "shell", path: "provision.sh"
end
