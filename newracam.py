#Dareun Geum
#2019/1/22

import traceback
import io
import sys
import socket
import time
import picamera

VERSION = "v1.1.3"
print("/////////////////////")
print("// NEWRACAM " + VERSION + " //")
print("/////////////////////")

def printUsage():
    print("+++USAGE+++\n")
    print("    newracam {opt1} {opt2} ... {optN}\n")
    print("")
    print("+++MANDATORY ARGUMENTS+++\n")
    print("    OPTION        DESCRIPTION")
    print("    -n <argv>     (TCP/SERVER)     <argv> : server [Port]")
    print("                  (TCP/CLIENT)     <argv> : tcp [IP] [Port]")
    print("                  (UDP/CLIENT)     <argv> : udp [IP] [Port]")
    print("    *(UDP/CLIENT) mode only works with JPEG codec.")
    print("")
    print("    -c <codec>    (JPEG CODEC)     <codec> = jpeg, mjpeg, h264")
    print("")
    print("+++OPTIONAL ARGUMENTS+++")
    print("    -f <fps>      (FPS)>           <fps> = 1~30 (Default:10)")
    print("")
    print("    -q <quality>  (JPEG QUALITY)   <quality> = 1~100 (Default:5)")
    print("    *jpeg codec only")
    print("")
    print("    -w <width>    (IMAGE WIDTH)    (Default:320)")
    print("")
    print("    -h <height>   (IMAGE HEIGHT)   (Default:240)")
    print("")
    print("    -l <length>   (UDP LENGTH)     (Default:1400)")
    print("    *Use this parameter to avoid MTU and MCS auto-change")
    print("")
    print("    -hp           (HEADER PACKET) (Default: Headerless)")
    print("    *Sends a 7-byte header UDP packet prior to sending the first UDP ")
    print("     image fragment packet. The first 4 bytes repeats the JPEG start ")
    print("     magic bytes 0xFFD8 twice. The remaining 3 bytes corresponds to ")
    print("     the total length of the image bytes that directly follows over ")
    print("     multiple fragmented UDP packets in LSB order.")
    print("")
    print("+++AUTO-RATE ARGUMENTS+++")
    print("    -a <port>     (AUTO_RATE)")
    print("    *This will enable automatic rate adaptation mode for UDP JPEG")
    print("     mode. Parameters related to image quality (WIDTH, HEIGHT, FPS")
    print("     and QUALITY) will change dynamically by adapting to the receiver")
    print("     UDP response periodically read from the datagram server on <port>.")
    print("     The receiver should periodically send the reponse packet roughly")
    print("     at the rate of one packet per second. The response packet text is ")
    print("     simply the number of accumulated count of total images received.")
    print("     (WIDTH, HEIGHT, FPS, QUALITY, LENGTH) should not be explicitly set")
    print("     when automatic rate adaptation mode is enabled.")
    print("")
    print("    -up <period>  (UPDATE PERIOD) (Default:10)")
    print("    *Sets the quality update period of AUTO-RATE mode in seconds.")
    print("     Each update will most likely cause a temporary stutter which lasts")
    print("     for approximately 1 second. Therefore, a value less than 10 may ")
    print("     result in noticably frequent stutters.")
    print("")
    print("+++EXAMPLES+++\n")
    print("python newracam.py -n udp 192.168.10.29 12345 -c jpeg -w 320 -h 240 -q 20\n")
    print("    => udp/client mode streaming JPEG images to 192.168.10.29:12345")
    print("       using 320x240 image size and 20% JPEG quality.\n")
    print("python newracam.py -n server 12345 -c mjpeg -w 640 -h 480\n")
    print("    => tcp/server (port = 12345) mode with MotionJPEG codec using")
    print("       640x480 image size.")
 
if len(sys.argv)==1:
    printUsage()
    sys.exit(0)

#############################
# Argument Parsing  - START #
#############################

argv = sys.argv[1:]
argc = len(argv)
argi = 0

NETWORK = None
IP = None
PORT = None
CODEC = None
WIDTH = None
HEIGHT = None
QUALITY = None
SEGMENT_SIZE = None
FPS = None
AUTO_RATE = None
USE_HEADER_FRAME = None
UPDATE_PERIOD = None

try:
    while argi < argc:
        if argv[argi]=="-n":
            if not NETWORK == None:
                    raise Exception("DUPLICATE PARAMETER :" + argv[argi])    
            argi+=1
            if argv[argi]=="server":
                NETWORK = "tcp(server)"
                IP = "0.0.0.0"
                argi+=1
                PORT = int(argv[argi])
                argi+=1
            elif argv[argi]=="tcp":
                NETWORK = "tcp(client)"
                argi+=1
                IP = argv[argi]
                argi+=1
                PORT = int(argv[argi])
                argi+=1
            elif argv[argi]=="udp":
                NETWORK = "udp(client)"
                argi+=1
                IP = argv[argi]
                argi+=1
                PORT = int(argv[argi])
                argi+=1
            else:
                raise Exception("UNKNOWN NETWORK TYPE : " + argv[argi])
        elif argv[argi] == "-c":
            if not CODEC == None:
                raise Exception("DUPLICATE PARAMETER : " + argv[argi])
            argi+=1
            if argv[argi] in ["jpeg", "mjpeg", "h264"]:
                CODEC = argv[argi]
                argi+=1
            else:
                raise Exception("UNKNOWN CODEC : " + argv[argi])
        elif argv[argi] == "-w":
            if not WIDTH == None:
                raise Exception("DUPLICATE PARAMETER : " + argv[argi])
            argi+=1
            WIDTH = int(argv[argi])
            argi+=1
        elif argv[argi] == "-h":
            if not HEIGHT == None:
                raise Exception("DUPLICATE PARAMETER : " + argv[argi])
            argi+=1
            HEIGHT = int(argv[argi])
            argi+=1
        elif argv[argi] == '-q':
            if not QUALITY == None:
                raise Exception("DUPLICATE PARAMETER : " + argv[argi])
            argi+=1
            QUALITY = int(argv[argi])
            argi+=1
        elif argv[argi] == '-f':
            if not FPS == None:
                raise Exception("DUPLICATE PARAMETER : " + argv[argi])
            argi+=1
            FPS = int(argv[argi])
            argi+=1
        elif argv[argi] == '-l':
            if not SEGMENT_SIZE == None:
                raise Exception("DUPLICATE PARAMETER : " + argv[argi])
            argi+=1
            SEGMENT_SIZE = int(argv[argi])
            argi+=1
        elif argv[argi] == '-a':
            if not AUTO_RATE == None:
                raise Exception("DUPLICATE PARAMETER : " + argv[argi]) 
            AUTO_RATE = True
            argi+=1
            AUTO_RATE_PORT = int(argv[argi])
            argi+=1
        elif argv[argi] == '-hp':
            if not USE_HEADER_FRAME == None:
                raise Exception("DUPLICATE PARAMETER : " + argv[argi])
            USE_HEADER_FRAME = True
            argi+=1
        elif argv[argi] == '-up':
            if not UPDATE_PERIOD == None:
                raise Exception("DUPLICATE PARAMETER : " + argv[argi])
            argi+=1
            UPDATE_PERIOD = int(argv[argi])
            argi+=1
        else:
            raise Exception("UNKNOWN PARAMETER : " + argv[argi])
            sys.exit(1)
    
    ###########################
    ## ARGUMENT VERIFICATION ##
    ###########################

    MISSING_ARGUMENT_LIST = []
    if NETWORK == None:
        MISSING_ARGUMENT_LIST.append("NETWORK")
    if CODEC == None: 
        MISSING_ARGUMENT_LIST.append("CODEC")
    if len(MISSING_ARGUMENT_LIST) > 0:
        ERROR_MESSAGE = "The following mandatory arguments are missing:\n\n"
        for MISSING_ARGUMENT in MISSING_ARGUMENT_LIST:
            ERROR_MESSAGE += (MISSING_ARGUMENT + " ")
        ERROR_MESSAGE += "\n"
        raise Exception(ERROR_MESSAGE)

    if NETWORK == "udp(client)" and not CODEC == "jpeg":
       raise Exception("udp(client) only works with jpeg codec!\n")
    if not NETWORK == "udp(client)" and not CODEC == "jpeg" and AUTO_RATE:
       raise Exception("Rate adaptation only works with UDP JPEG!")
    if not CODEC == "jpeg" and not QUALITY == None:
       raise Exception("Quality parameter is only applicable to jpeg codec!\n")
    if AUTO_RATE and (not WIDTH==None or not HEIGHT==None or not FPS == None or not QUALITY == None or not SEGMENT_SIZE == None):
       raise Exception("(WIDTH, HEIGHT, FPS, QUALITY, SEGMENT_SIZE) should not be explicitly set when AUTO_RATE is enabled!")
    if not NETWORK == "udp(client)" and not CODEC == "jpeg" and not USE_HEADER_FRAME == None:
        raise Exception("Header frame is only available for udp(client)/jpeg!")
    if not AUTO_RATE and not UPDATE_PERIOD == None:
        raise Exception("Update period cannot be set unless AUTO_RATE is enabled!")

    ####################
    ## DEFAULT VALUES ##
    ####################
    AUTO_RATE_LEVEL = 10

    if QUALITY==None and CODEC == "jpeg":
        QUALITY = 5
    if CODEC == 'jpeg' and NETWORK == "udp(client)":
        if SEGMENT_SIZE == None:
            SEGMENT_SIZE = 1400
        if USE_HEADER_FRAME == None:
            USE_HEADER_FRAME = False
    if FPS==None and not AUTO_RATE:
        FPS = 5
    if WIDTH==None and not AUTO_RATE:
        WIDTH = 320
    if HEIGHT==None and not AUTO_RATE:
        HEIGHT = 240
    if AUTO_RATE == None:
        AUTO_RATE = False
    if AUTO_RATE:
        WIDTH = 640
        HEIGHT = 480
        QUALITY = 9
        FPS = 10
    if AUTO_RATE and UPDATE_PERIOD == None:
        UPDATE_PERIOD = 10

except Exception as e:
    print("+ERROR+\n")
    print(e)
    print("")
    printUsage()
    sys.exit(1)

print("      NETWORK : " + str(NETWORK))
print("           IP : " + str(IP))
print("         PORT : " + str(PORT))
print("        CODEC : " + str(CODEC))
if AUTO_RATE:
    print("UPDATE PERIOD : " + str(UPDATE_PERIOD))    
else:
    print("        WIDTH : " + str(WIDTH))
    print("       HEIGHT : " + str(HEIGHT))
    print("      QUALITY : " + str(QUALITY))
    print("          FPS : " + str(FPS))

print("     UDP SIZE : " + str(SEGMENT_SIZE))
print("    AUTO-RATE : " + str(AUTO_RATE))
print("       HEADER : " + str(USE_HEADER_FRAME))

#############################
# Argument Parsing  -  END  #
#############################

#############################
#  Rate Adaptation - START  #
#############################

DIMENSIONS = [(320, 240), (640, 480), (960, 720)]

# 0:DIMENSION, 1:QUALITY, 2:FPS, 4:SEGMENT_SIZE
AUTO_RATE_TABLE = [
    [0, 5, 5, 400],
    [0, 5, 5, 650],
    [0, 5, 5, 1400],
    [0, 6, 6, 1400],
    [0, 7, 7, 1400],
    [0, 8, 8, 1400],
    [0, 9, 9, 1400],
    [0, 10, 10, 1400],
    [1, 5, 10, 1400],
    [1, 6, 10, 1400],
    [1, 7, 10, 1400],
    [1, 8, 10, 1400],
    [1, 9, 10, 1400],
    [1, 10, 10, 1400],
]

PREV_STATS = None

def receiveStats():
    try:
        data, addr = SOCKET.recvfrom(1024)
        return (int(data), time.time())
    except:
        return None

def applyAutoRateLevel(level):
    global AUTO_RATE_LEVEL
    if level < 0:
        level = 0
    elif level >= len(AUTO_RATE_TABLE):
        level = len(AUTO_RATE_TABLE)-1
    if AUTO_RATE_LEVEL == level:
        if AUTO_RATE_LEVEL == len(AUTO_RATE_TABLE) - 1:
            print("MAXIMUM LEVEL REACHED")
        elif AUTO_RATE_LEVEL == 0:
            print("MINIMUM LEVEL REACHED")
        else:
            print("STAYING AT THE SAME LEVEL")
        return False
    print("CHANGING LEVEL: %d => %d"%(AUTO_RATE_LEVEL, level))
    AUTO_RATE_LEVEL = level 
    AUTO_RATE = AUTO_RATE_TABLE[level]
    DIMENSION = DIMENSIONS[AUTO_RATE[0]]
    global WIDTH, HEIGHT, QUALITY, FPS, SEGMENT_SIZE, SEND_FAIL_COUNT, PREV_STATS
    WIDTH = DIMENSION[0]
    HEIGHT = DIMENSION[1]
    QUALITY = AUTO_RATE[1]
    FPS = AUTO_RATE[2]
    SEGMENT_SIZE = AUTO_RATE[3]
    camera.resolution = DIMENSION
    camera.framerate = FPS
    SEND_FAIL_COUNT = 0
    return True

def updateAutoRateLevel():
    stats = receiveStats()
    global SEND_FAIL_COUNT
    if SEND_FAIL_COUNT > 1:
        SEND_FAIL_COUNT = 0
        return applyAutoRateLevel(AUTO_RATE_LEVEL-2)

    if not stats == None:
        print("DATAGRAM PACKET RECEIVED : " + str(stats))
        pass
    
    if stats == None:
        return False
    global PREV_STATS
    if PREV_STATS == None:
        PREV_STATS = stats
    else:
        TIME = stats[1] - PREV_STATS[1]
        if TIME < UPDATE_PERIOD:
            return False
        if TIME<=0:
            return False
        COUNT = stats[0] - PREV_STATS[0]
        PREV_STATS = stats
        #print(COUNT, TIME, FPS)
        SUCCESS_RATE = COUNT / (TIME * FPS)
        print("SUCCESS RATE => " + str(SUCCESS_RATE))
        if SUCCESS_RATE > 1.1:
            return False
        elif SUCCESS_RATE > 0.9:
            return applyAutoRateLevel(AUTO_RATE_LEVEL+1)
        elif SUCCESS_RATE < 0.7:
            return applyAutoRateLevel(AUTO_RATE_LEVEL-2)
    return False

#############################
#  Rate Adaptation  -  END  #
#############################

#############################
#     Operation     - START #
#############################

def createLengthFrame(length):
    return bytes([0xFF, 0xD8, 0xFF, 0xD8, length & 0xFF, length>>8 & 0xFF, length>>16 & 0xFF])

try:
    ############
    ## CAMERA ##
    ############
    camera = picamera.PiCamera()
    camera.resolution = (WIDTH, HEIGHT)
    camera.framerate = FPS
    #time.sleep(2)

    #############
    ## NETWORK ##
    #############

    if NETWORK == "tcp(server)":
        SOCKET = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        SOCKET.bind((IP, PORT))
        print("SERVER IS LISTENING...")
        SOCKET.listen(0)
        (ioSocket, address) = SOCKET.accept()
        (clientIP, clientPort) = address
        print(str(clientIP) + ":" + str(clientPort) + " HAS CONNECTED!")
        STREAM = ioSocket.makefile('wb') 
    elif NETWORK == "tcp(client)":
        SOCKET = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        print("ATTEMPTING CONNECTION to " + IP + ":" + str(PORT))
        SOCKET.connect((IP, PORT))
        print("CONNECTION SUCCESSFUL!")
        STREAM = SOCKET.makefile('wb')
    elif NETWORK == "udp(client)":
        SOCKET = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        if AUTO_RATE:
            SOCKET.bind(("0.0.0.0", AUTO_RATE_PORT))
            SOCKET.setblocking(False)
    else:
        raise Exception("UNKNOWN NETWORK TYPE : " + NETWORK)
    
    ###############o
    ## STREAMING ##
    ###############

    if CODEC in ["mjpeg", "h264"]:
        if NETWORK in ["tcp(server)", "tcp(client)"]:
            print("STREAMING...")
            camera.start_recording(STREAM, format = CODEC, quality = 1)
            camera.wait_recording(99999999)
        elif NETWORK == "udp(client)":
            pass #TODO
    elif CODEC == "jpeg":
        if NETWORK in ["tcp(server)", "tcp(client)"]:
            print("STREAMING...")
            for _ in camera.capture_continuous(STREAM, 'jpeg', use_video_port = True, quality = QUALITY):
                pass #Does not require a body. 
        elif NETWORK == "udp(client)":
            print("STREAMING...")
            while True:
                byteBuffer = io.BytesIO()
                SEND_FAIL_COUNT = 0
                for i in camera.capture_continuous(byteBuffer, 'jpeg', use_video_port = True, quality = QUALITY):
                    TOTAL_SIZE = byteBuffer.tell()
                    print("(IMAGE SIZE = %d Bytes)\t(REQUIRED DATA RATE = %.2f Kbits)"%(TOTAL_SIZE, TOTAL_SIZE*8*FPS/1024))
                    if USE_HEADER_FRAME:
                        while True:
                            try:
                                SOCKET.sendto(createLengthFrame(TOTAL_SIZE), (IP, PORT))
                                break
                            except:
                                print("HEADER FRAME RETRY")
                                time.sleep(0.1)
                    SEGMENT_COUNT = int(TOTAL_SIZE/SEGMENT_SIZE)
                    SEGMENT_REMAINDER = TOTAL_SIZE%SEGMENT_SIZE
                    if SEGMENT_REMAINDER > 0:
                        SEGMENT_COUNT+=1
                    byteBuffer.seek(0)
                    try:
                        first = True
                        for i in range(SEGMENT_COUNT):
                            if SEGMENT_REMAINDER > 0 and i == SEGMENT_COUNT -1:
                                SEND_SIZE = SEGMENT_REMAINDER
                            else:
                                SEND_SIZE = SEGMENT_SIZE
                            segment = byteBuffer.read(SEND_SIZE)
                            while True:
                                try:
                                    SOCKET.sendto(segment, (IP, PORT))
                                    break
                                except:
                                    if first:
                                        print("IMAGE FRAME RETRY")
                                        SEND_FAIL_COUNT+=1
                                        first = False
                                    time.sleep(0.1)
                    except Exception as e:
                        print(e)
                    byteBuffer.seek(0)
                    if AUTO_RATE:
                        if updateAutoRateLevel():
                            print("NEW AUTO_RATE LEVEL = " + str(AUTO_RATE_LEVEL))
                            break
        else:
            raise Exception("UNKNOWN NETWORK TYPE : " + NETWORK)
    else:
        raise Exception("UNKNOWN CODEC : " + CODEC)
except Exception as e:
    traceback.print_exc()
    print("Internal Error : " + str(e))
    sys.exit(1)

#############################
#     Operation     -  END  #
#############################


