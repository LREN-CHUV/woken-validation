#!/usr/bin/python2

import argparse
import json
import sys
import os

from titus.genpy import PFAEngine
from titus.datatype import AvroRecord, AvroString
from titus.errors import *

import logging
from logging import FileHandler, StreamHandler

log = logging.getLogger('')
log.addHandler(StreamHandler(sys.stdout))


def load_document(file):
    # Check that the file passed by the user exists
    if not os.path.exists(file):
        log.error("The path you provided does not exist:" + os.path.abspath(file))
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
        log.error("The file provided does not contain a valid JSON document: " + str(ex) + "; json_string was " + json_string)
        sys.exit(1)
    except PFASyntaxException as ex:
        # Syntax validation
        log.error("The file provided does not contain a valid PFA compliant document: " + str(ex) + "; json_string was " + json_string)
        sys.exit(1)
    except PFASemanticException as ex:
        # PFA semantic check
        log.error("The file provided contains inconsistent PFA semantics: " + str(ex) + "; json_string was " + json_string)
        sys.exit(1)
    except PFAInitializationException as ex:
        # Scoring engine check
        log.error("It wasn't possible to build a valid scoring engine from the PFA document: " + str(ex) + "; json_string was " + json_string)
        sys.exit(1)
    except Exception as ex:
        # Other exceptions
        log.error("An unknown exception occurred: " + str(ex) + "; json_string was " + json_string)
        sys.exit(1)

    # Check that the PFA file uses the "map" method. Other methods are not supported
    # (because irrelevant) by the MIP
    if not engine.config.method == "map":
        log.error("The PFA method you used is not supported. Please use the PFA 'map' method")
        sys.exit(1)

    # Check that the PFA file uses a "record" type as input
    if not isinstance(engine.config.input, AvroRecord):
        log.error("The PFA document must take a record as input parameter. " \
                      "Each field of the record must describe a variable")
        sys.exit(1)

    # Check that the PFA file has a least one input field
    if not engine.config.input.fields:
        log.error("The PFA document must describe an input record with at least one field")
        sys.exit(1)

    return engine


def main(pfa_file, data_file, results_file):

    engine = get_engine(load_document(pfa_file))
    data = json.loads(load_document(data_file))
    string_output = isinstance(engine.config.output, AvroString)

    with open(results_file, "w") as results:
        results.write("[")
        first = True
        for item in data:
            try:
                prediction = engine.action(item)
            except Exception as ex:
                log.error("Cannot make prediction on " + str(item) + ", error was " + str(ex))
                raise
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

    try:
        main(args.pfa_file, args.data_file, args.results_file)
    except Exception as e:
        log.exception(e)
        sys.exit(1)
