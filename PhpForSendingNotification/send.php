<?php 
$ch=curl_init("https://fcm.googleapis.com/fcm/send");
$header=array("Content-Type:application/json","Authorization: key=AAAArltD5_A:APA91bFp_TdgxnJLsIEXjm2OG9Zvo0eSAH-qvYRMtDGhCyIkPblY1RTJu_j3U1i5mjRNmGQJSJH-7iv1DfhQQwgELZ1y0KYtyHhrZus3T91dEmHio2rvQmRgXtJmA81HDrAIZApBkjBh");
$data=json_encode(array("to"=>"/topics/allDevices","notification"=>array("title"=>$_REQUEST['title'])));
curl_setopt($ch,CURLOPT_HTTPHEADER,$header);
curl_setopt($ch,CURLOPT_SSL_VERIFYPEER,false);
curl_setopt($ch,CURLOPT_POST,1);
curl_setopt($ch,CURLOPT_POSTFIELDS,$data);
curl_exec($ch);