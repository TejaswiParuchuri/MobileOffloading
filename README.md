# MobileOffloading


**Note** : This code must be executed by connecting to a device, it doesn’t work in emulator.

### 1.	Server Connectivity:
##### Local server setup:
   - Download Xampp and install it in the system
  - Place the give send.php file in xampp->htdocs->fcm folder 
   - start xampp_start.exe and then xampp_control.exe
   - Once Xampp controller starts start the apache sever
##### Checking local server connection:
   - To check if apache has started or not open any browser and type localhost it should show apache dashboard. It means sever started successfully
   - Then take system IP address in which local sever is started and replace the ip address in MainActivity.java file in code at line 323 and URL should something like this "http://xxx.xxx.x.xx/ fcm/send.php?title=Master%20needs%20your%20help%20for%20matrix%20multiplication". 
   - Now try the same URL in web browser it should send notification to all the devices in which this app is installed
##### send.php:
   - This file is placed in the PhpForSendingNotification folder. It must be placed in xampp->htdocs->fcm folder

### 2.	Project Code:
    Project code is there in AndroidStudioCode folder.
### 3.	Mobile App:
    Installable apk is there in MobileApp folder. That app can be directly installed in mobile devices for testing
   - Prerequisite:
     After the app is installed for the first time, we must give location permissions in the settings. To give location permissions go to Settings->Apps->MobileOffloading_Group15->Permissions. Enable Location under Permissions. It is a onetime requirement.

### 4	Videos:
Below drive has videos without audio which are clearer.

https://drive.google.com/drive/folders/1jpUJNwaNDLTt_sw9me3e8pluK57Zplqj?usp=sharing

YouTube link below has a single demo video (all the 4 videos are merged)

https://youtu.be/BQti6aMJ_vo



 
