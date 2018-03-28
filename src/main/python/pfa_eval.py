#!/usr/bin/python2

import logging
import argparse
import json
import sys
import os

from titus.genpy import PFAEngine
from titus.datatype import AvroRecord, AvroString
from titus.errors import *

def load_document(file):
    # Check that the file passed by the user exists
    if not os.path.exists(file):
        print_error("The path you provided does not exist:" + os.path.abspath(file))
        sys.exit(1)

    with open(file, 'r') as content_file:
        content_string = content_file.read()
    return content_string


def get_engine(json_string):
    """Creates a PFA engine based on the json_string provided as constructor class. If an
    engine was already created, this method does nothing"""

    try:
        # pylint: disable=unbalanced-tuple-unpacking
        engine, = PFAEngine.fromJson(json.loads(json_string))

    except ValueError as ex:
        # JSON validation
        logging.error("The file provided does not contain a valid JSON document: " + str(ex))
        sys.exit(1)
    except PFASyntaxException as ex:
        # Syntax validation
        logging.error("The file provided does not contain a valid PFA compliant document: " + str(ex))
        sys.exit(1)
    except PFASemanticException as ex:
        # PFA semantic check
        logging.error("The file provided contains inconsistent PFA semantics: " + str(ex))
        sys.exit(1)
    except PFAInitializationException as ex:
        # Scoring engine check
        logging.error("It wasn't possible to build a valid scoring engine from the PFA document: " + str(ex))
        sys.exit(1)
    except Exception as ex:
        # Other exceptions
        logging.error("An unknown exception occurred: " + str(ex))
        sys.exit(1)

    # Check that the PFA file uses the "map" method. Other methods are not supported
    # (because irrelevant) by the MIP
    if not engine.config.method == "map":
        logging.error("The PFA method you used is not supported. Please use the PFA 'map' method")
        sys.exit(1)

    # Check that the PFA file uses a "record" type as input
    if not isinstance(engine.config.input, AvroRecord):
        logging.error("The PFA document must take a record as input parameter. " \
                      "Each field of the record must describe a variable")
        sys.exit(1)

    # Check that the PFA file has a least one input field
    if not engine.config.input.fields:
        logging.error("The PFA document must describe an input record with at least one field")
        sys.exit(1)

    return engine

def main(pfa_file, data_file, results_file):

    engine = get_engine(load_document(pfa_file))
    data = json.loads(load_document(data_file))
    string_output = isinstance(engine.config.output, AvroString)

    with open(results_file,"w") as results:
        results.write("[")
        first = True
        for item in data:
            prediction = engine.action(item)
            if first:
              first = False
            else:
                results.write(',')
            if string_output:
                results.write('"%s"' % str(prediction))
            else:
                results.write("%s" % str(prediction))
        results.write("]")

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Make predictions from data by evaluating a PFA model.')
    parser.add_argument('pfa_file')
    parser.add_argument('data_file')
    parser.add_argument('results_file')

    args = parser.parse_args()

    main(args.pfa_file, args.data_file, args.results_file)
