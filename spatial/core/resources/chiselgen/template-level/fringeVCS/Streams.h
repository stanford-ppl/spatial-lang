#ifndef STREAMS_H
#define STREAMS_H

#include <unistd.h>
#include <errno.h>
#include <cstring>
#include <string>
#include <cstdlib>
#include <stdio.h>
#include <iostream>
#include <fstream>
#include <vector>
#include <queue>
#include <map>
#include <poll.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/mman.h>
#include <sys/prctl.h>

#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>


using namespace std;

#include "vc_hdrs.h"
#include "svdpi_src.h"

// TODO: Move member functions to seprate CPP files. Don't leave in header...


static bool stream_done = false;

enum StreamType {
	FILE_STREAM,
	ETHERNET_STREAM
};


class InputStream {

public: 
  virtual void send() = 0;

};

class OutputStream {
  
public:
  virtual void recv(uint32_t udata, uint32_t tag, bool blast) = 0;

};


class FileInputStream : public InputStream {
public:
  string filename;
  int fd;
  ifstream ifs;

  uint32_t next;
  bool lastEncountered;

  FileInputStream(string filename);
  ~FileInputStream();

  virtual void send();

};

FileInputStream::FileInputStream(string filename) : filename(filename) {

    // Open input file stream    
    ifs.open(filename, ifstream::in);
    ASSERT(ifs.is_open(), "[InputStream] Error opening file '%s': %s\n", filename.c_str(), strerror(errno));
    
    // Might need this variables later
    next = 0;
    lastEncountered = false;
}

void FileInputStream::send() {
    
    // Initialization
    uint32_t data = 0;
    static uint32_t tag = 0;
    bool last = false;

    // Read data from file
    ifs >> data;    

    // Check if all of file has been read
    ASSERT( !ifs.bad(), "[InputStream] Error reading file '%s': %s\n", filename.c_str(), strerror(errno));
    if (ifs.eof()) {
      if (lastEncountered) { // If we have already seen last, don't send it again
        last = false;
	stream_done = true;
      } 
      else {
        last = true;
        lastEncountered = true;
      }
    } 
    else {
      last = false;
    }

    // Write data from file to Top module
    writeStream(data, tag++, last);
  }

FileInputStream::~FileInputStream() {
    ifs.close();
}





class FileOutputStream : public OutputStream {
public:
  string filename;
  int fd;
  ofstream ofs;


  FileOutputStream(string filename);
  ~FileOutputStream();

  virtual void recv(uint32_t udata, uint32_t tag, bool blast);

};

FileOutputStream::FileOutputStream(string filename) : filename(filename) {
    
    // Open output file stream
    ofs.open(filename, ofstream::out);
    ASSERT(ofs.is_open(), "[OutputStream] Error opening file '%s': %s\n", filename.c_str(), strerror(errno));
}


// Callback function called from SV -> sim.cpp
// Just write data into a file
// [TODO] In current scheme, output stream is always ready. Model backpressure more accurately.
void FileOutputStream::recv(uint32_t udata, uint32_t utag, bool blast) {
 
    // If there is data, write it to the file. Otherwise, close the file
    if (!stream_done){
    	ofs << udata;
    	ofs << "\n";
    }
    else
    {
	if (ofs.is_open()){
		ofs.close();
	}
    }
    ASSERT(!ofs.fail(), "[OutputStream] Error writing to file '%s': %s\n", filename.c_str(), strerror(errno));
}

FileOutputStream::~FileOutputStream() {
    ofs.close();
    if (ofs.is_open()){
	ofs.close();
    }
}


class EthernetInputStream : public InputStream {
public:
  int port;
  int server_fd;
  int connection;
  int valread;
  struct sockaddr_in address;
  int socket_option = 1;
  int addrlen = sizeof(address);



  EthernetInputStream(int port);
  ~EthernetInputStream();

  virtual void send();

};

EthernetInputStream::EthernetInputStream(int port) : port(port) {

        // Start server and bind it to socket 
        // Creating socket file descriptor
	int ret;
        server_fd = socket(AF_INET, SOCK_STREAM, 0);
        ASSERT(server_fd != 0, "[InputStream] Error creating server file descriptor: %s\n", strerror(errno));
       
	// Set socket options
	ret = setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR | SO_REUSEPORT,
				&socket_option, sizeof(socket_option));
        ASSERT(ret == 0, "[InputStream] Error setting socket options: %s\n", strerror(errno));
	
	address.sin_family = AF_INET;
	address.sin_addr.s_addr = INADDR_ANY;
	address.sin_port = htons(port);

	// Bind socket to port
	if (bind(server_fd, (struct sockaddr *)&address, sizeof(address))<0)
	ret = bind(server_fd, (struct sockaddr *)&address, sizeof(address));
	ASSERT(ret >= 0, "[InputStream] Error binding socket to port %d : %s\n", port, strerror(errno));
	
	// Listen on port
	ret = listen(server_fd, 3);
	ASSERT(ret >= 0, "[InputStream] Error listening on port %d : %s\n", port, strerror(errno));
	
	// Block till a connection is accepted
        EPRINTF("[InputStream] Waiting for input connection\n");
	connection = accept(server_fd, (struct sockaddr *)&address, (socklen_t*)&addrlen);
	ASSERT(connection >= 0, "[InputStream] Error accepting a connection on port %d : %s\n", port, strerror(errno));

	
	
        EPRINTF("[InputStream] Successfully created input connection\n");

}

void EthernetInputStream::send() {


    struct test_pkt {
	uint32_t data;
	uint32_t done;
    };
    
    // Initialization
    uint32_t data = 0;
    static uint32_t tag = 0;
    bool last = false;
    struct test_pkt pkt;
    pkt.data = 0;
    pkt.done = 0;
    int ret = 1;


    if (!stream_done) {
	    ret = read(connection, &pkt, sizeof(test_pkt));
    	    EPRINTF("************* PKT_DATA: %d\n", pkt.data);
    	    EPRINTF("************* PKT_DONE: %d\n", pkt.done);
    	    EPRINTF("************* RET: %d\n", ret);
    }

    if (pkt.done != 0 || ret == 0){
    	stream_done = true;
    }

    // Write data from file to Top module
    writeStream(pkt.data, tag++, last);

}


EthernetInputStream::~EthernetInputStream() {
    close(server_fd);
}



class EthernetOutputStream : public OutputStream {
public:
  int port;
  struct sockaddr_in address;
  int sock = 0;
  struct sockaddr_in serv_addr;

  int ctr = 0;

  EthernetOutputStream(int port);
  ~EthernetOutputStream();

  virtual void recv(uint32_t udata, uint32_t tag, bool blast);

};

EthernetOutputStream::EthernetOutputStream(int port) : port(port) {

	int ret;        

	// Create client socket for output streams
	sock = socket(AF_INET, SOCK_STREAM, 0);
        ASSERT(sock >= 0, "[OutputStream] Error creating output socket: %s\n", strerror(errno));

        memset(&serv_addr, '0', sizeof(serv_addr));
        serv_addr.sin_family = AF_INET;
        serv_addr.sin_port = htons(port);

        // For now, send to self
        ret = inet_pton(AF_INET, "127.0.0.1", &serv_addr.sin_addr);
        ASSERT(ret > 0, "[OutputStream] Error setting server address: %s\n", strerror(errno));

	// Connect to server
        EPRINTF("[InputStream] Waiting for output connection\n");
        ret = connect(sock, (struct sockaddr *)&serv_addr, sizeof(serv_addr));
        ASSERT(ret >= 0, "[OutputStream] Error connecting to server: %s\n", strerror(errno));
        EPRINTF("[InputStream] Successfully created output connection\n");

}

void EthernetOutputStream::recv(uint32_t data, uint32_t tag, bool blast) {


    struct test_pkt {
	uint32_t data;
	uint32_t done;
    };
    
    // Initialization
    struct test_pkt pkt;
    pkt.data = data;
    pkt.done = 0;
    //int ret = 1;

    if (!stream_done || ctr < 3) {
    	    EPRINTF("============= PKT_DATA: %d\n", pkt.data);
    	    EPRINTF("============= PKT_DONE: %d\n", pkt.done);
	    send(sock , &pkt , sizeof(test_pkt) , 0 );
    }
 

    if (stream_done)
	ctr++;

}


EthernetOutputStream::~EthernetOutputStream() {
    close(sock);
}







// Input stream
InputStream *inStream = NULL;

// Output stream
OutputStream *outStream = NULL;

void initStreams(StreamType type); 

void initInputStream(StreamType type); 
void initOutputStream(StreamType type); 

void initInputStream(StreamType type) {


  // Initialize simulation streams

  switch (type) {

    case FILE_STREAM:
      inStream = new FileInputStream("in.txt");
      break;
 
    case ETHERNET_STREAM:
      inStream = new EthernetInputStream(8080);
      break;

  }

}

void initOutputStream(StreamType type) {


  // Initialize simulation streams

  switch (type) {

    case FILE_STREAM:
      outStream = new FileOutputStream("out.txt");
      break;
 
    case ETHERNET_STREAM:
      outStream = new EthernetOutputStream(8081);
      break;

  }

}


void initStreams(StreamType type) {


  // Initialize simulation streams

  switch (type) {

    case FILE_STREAM:
      inStream = new FileInputStream("in.txt");
      outStream = new FileOutputStream("out.txt");
      break;
 
    case ETHERNET_STREAM:
      break;

  }

}

#endif
