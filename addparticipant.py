import sys
from datetime import datetime
PARTICIPANT_DIR = "./participant_data/"

def main():
    if sys.argv[1] == "add":
        # Get participant name
        part_name = ""
        for i in range(2, len(sys.argv)):
            part_name += sys.argv[i] + " "

        f = open(PARTICIPANT_DIR + "participants.csv", 'r+')
        participants = [line.split(",") for line in f]
        ids = [part[1] for part in participants]
	ids = map(int, ids)
        ids.sort()
        if(len(ids) == 0):  next_id = 0
        else:   next_id = int(ids[-1]) + 1
        f.write(str(part_name) + ", " + str(next_id) + ", " + datetime.now().isoformat() + "\n");
        f.close()
        print "Added participant " + part_name + " with id " + str(next_id)

    if sys.argv[1] == "delete":
        part_name = ""
        for i in range(2, len(sys.argv)):
            part_name += sys.argv[i] + " "
        f = open(PARTICIPANT_DIR + "participants.csv", 'r')
        participants = [line for line in f]
        f.close()

        for part in participants:
            if part_name in part.split(',')[0]:
                participants.remove(part)
                f = open(PARTICIPANT_DIR + "participants.csv", 'w')
                for part in participants: f.write(part)
                f.close()
                print "Deleted:"
                print part
                return

    if sys.argv[1] == "lookup":
        part_name = ""
        for i in range(2, len(sys.argv)):
            part_name += sys.argv[i] + " "
        f = open(PARTICIPANT_DIR + "participants.csv", 'r')
        participants = [line for line in f]

        for part in participants:
            if part_name in part.split(',')[0]:
                print part




if __name__ == "__main__":
    main()
